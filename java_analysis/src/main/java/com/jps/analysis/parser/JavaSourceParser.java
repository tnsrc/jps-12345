package com.jps.analysis.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.jps.analysis.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class JavaSourceParser {
    private static final Logger logger = LoggerFactory.getLogger(JavaSourceParser.class);
    private final DatabaseManager dbManager;
    private final Map<String, Integer> classCache;
    private final Map<String, Integer> methodCache;
    private final Stack<ClassOrInterfaceDeclaration> classStack;
    private final Stack<MethodDeclaration> methodStack;
    private final Stack<Node> statementStack;
    private final Map<String, String> parameterTypes;
    private Path projectRoot;
    private String packageName;
    private Map<String, String> importMap = new HashMap<>();

    public JavaSourceParser() {
        this.dbManager = DatabaseManager.getInstance();
        this.classCache = new HashMap<>();
        this.methodCache = new HashMap<>();
        this.classStack = new Stack<>();
        this.methodStack = new Stack<>();
        this.statementStack = new Stack<>();
        this.parameterTypes = new HashMap<>();
    }

    public void parseProject(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot;
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(projectRoot));
        
        JavaParser parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));

        Files.walk(projectRoot)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> {
                    try {
                        parseJavaFile(path, parser);
                    } catch (IOException e) {
                        logger.error("Failed to parse file: " + path, e);
                    }
                });
    }

    public void parseJavaFile(Path filePath, JavaParser parser) throws IOException {
        if (projectRoot == null) {
            projectRoot = filePath.getParent();
        }
        
        // Read the file content
        String content = Files.readString(filePath);
        
        // Parse the file
        ParseResult<CompilationUnit> result = parser.parse(content);
        
        if (result.isSuccessful()) {
            CompilationUnit cu = result.getResult().get();
            
            // Get package name
            packageName = cu.getPackageDeclaration()
                    .map(pd -> pd.getNameAsString())
                    .orElseGet(() -> {
                        Path relativePath = projectRoot.relativize(filePath.getParent());
                        String derivedPackage = relativePath.toString().replace('/', '.');
                        return derivedPackage;
                    });
            
            // Build import map
            importMap.clear();
            cu.getImports().forEach(imp -> {
                String name = imp.getNameAsString();
                if (imp.isStatic()) {
                    // For static imports, use the last part as the key
                    String[] parts = name.split("\\.");
                    importMap.put(parts[parts.length - 1], name);
                } else {
                    // For regular imports, use the last part as the key
                    String[] parts = name.split("\\.");
                    importMap.put(parts[parts.length - 1], name);
                }
            });
            
            // Visit the compilation unit
            cu.accept(new VoidVisitorAdapter<Void>() {
                private final Stack<ClassOrInterfaceDeclaration> classStack = new Stack<>();

                @Override
                public void visit(ClassOrInterfaceDeclaration n, Void arg) {
                    classStack.push(n);
                    super.visit(n, arg);
                    classStack.pop();

                    String className = n.getNameAsString();
                    System.out.println("Found class: " + className);
                    boolean isAnonymous = n.getParentNode()
                            .map(p -> p instanceof ObjectCreationExpr)
                            .orElse(false);
                    boolean isNested = !classStack.isEmpty();
                    Integer parentClassId = null;
                    if (isNested) {
                        parentClassId = classCache.get(packageName + "." + 
                            classStack.peek().getNameAsString());
                    }

                    storeClass(packageName, className, n.toString(), filePath.toString(), 
                            isAnonymous, isNested, parentClassId);
                }

                @Override
                public void visit(MethodDeclaration n, Void arg) {
                    String className = classStack.peek().getNameAsString();
                    String classKey = packageName + "." + className;
                    System.out.println("Found method in class " + classKey + ": " + n.getNameAsString());
                    
                    // Store the class first if it doesn't exist in the cache
                    if (!classCache.containsKey(classKey)) {
                        ClassOrInterfaceDeclaration classDecl = classStack.peek();
                        boolean isAnonymous = classDecl.getParentNode()
                                .map(p -> p instanceof ObjectCreationExpr)
                                .orElse(false);
                        boolean isNested = !classStack.isEmpty();
                        Integer parentClassId = null;
                        if (isNested) {
                            parentClassId = classCache.get(packageName + "." + 
                                classStack.peek().getNameAsString());
                        }
                        storeClass(packageName, className, classDecl.toString(), filePath.toString(), 
                                isAnonymous, isNested, parentClassId);
                    }
                    
                    String methodName = n.getNameAsString();
                    String returnType = n.getType().toString();
                    String parameters = n.getParameters().stream()
                            .map(p -> p.getNameAsString())
                            .collect(Collectors.joining(",", "[", "]"));
                    String parameterTypes = n.getParameters().stream()
                            .map(p -> p.getType().toString())
                            .collect(Collectors.joining(","));
                    boolean isStatic = n.isStatic();
                    boolean isPublic = n.isPublic();
                    boolean isConstructor = n.isConstructorDeclaration();
                    boolean isInitializer = n.isInitializerDeclaration();
                    boolean isLambda = false;
                    boolean isAnonymous = false;

                    // Store the method before processing its body
                    storeMethod(classKey, methodName, returnType, parameters, parameterTypes,
                            isStatic, isPublic, isConstructor, isInitializer, isLambda, isAnonymous);

                    // Visit method body to find method calls
                    n.getBody().ifPresent(body -> {
                        body.accept(new VoidVisitorAdapter<Void>() {
                            private boolean inTryBlock = false;
                            private boolean inCatchBlock = false;
                            private boolean inFinallyBlock = false;
                            private boolean inLoop = false;
                            private String loopType = "";
                            private boolean inConditional = false;
                            private String conditionalType = "";

                            @Override
                            public void visit(TryStmt n, Void arg) {
                                boolean prevInTryBlock = inTryBlock;
                                boolean prevInCatchBlock = inCatchBlock;
                                boolean prevInFinallyBlock = inFinallyBlock;
                                
                                inTryBlock = true;
                                super.visit(n, arg);
                                inTryBlock = false;
                                
                                if (n.getCatchClauses() != null) {
                                    inCatchBlock = true;
                                    n.getCatchClauses().forEach(c -> c.accept(this, arg));
                                    inCatchBlock = false;
                                }
                                
                                if (n.getFinallyBlock().isPresent()) {
                                    inFinallyBlock = true;
                                    n.getFinallyBlock().get().accept(this, arg);
                                    inFinallyBlock = false;
                                }
                                
                                inTryBlock = prevInTryBlock;
                                inCatchBlock = prevInCatchBlock;
                                inFinallyBlock = prevInFinallyBlock;
                            }

                            @Override
                            public void visit(ForStmt n, Void arg) {
                                boolean prevInLoop = inLoop;
                                String prevLoopType = loopType;
                                
                                inLoop = true;
                                loopType = "for";
                                super.visit(n, arg);
                                
                                inLoop = prevInLoop;
                                loopType = prevLoopType;
                            }

                            @Override
                            public void visit(WhileStmt n, Void arg) {
                                boolean prevInLoop = inLoop;
                                String prevLoopType = loopType;
                                
                                inLoop = true;
                                loopType = "while";
                                super.visit(n, arg);
                                
                                inLoop = prevInLoop;
                                loopType = prevLoopType;
                            }

                            @Override
                            public void visit(DoStmt n, Void arg) {
                                boolean prevInLoop = inLoop;
                                String prevLoopType = loopType;
                                
                                inLoop = true;
                                loopType = "do";
                                super.visit(n, arg);
                                
                                inLoop = prevInLoop;
                                loopType = prevLoopType;
                            }

                            @Override
                            public void visit(IfStmt n, Void arg) {
                                boolean prevInConditional = inConditional;
                                String prevConditionalType = conditionalType;
                                
                                inConditional = true;
                                conditionalType = "if";
                                super.visit(n, arg);
                                
                                inConditional = prevInConditional;
                                conditionalType = prevConditionalType;
                            }

                            @Override
                            public void visit(MethodCallExpr n, Void arg) {
                                System.out.println("Found method call: " + n.getNameAsString());
                                super.visit(n, arg);
                                
                                String callerClass = classKey;
                                String callerMethod = methodName;
                                String callerParameters = parameters;
                                String calledMethod = n.getNameAsString();
                                String calledParameters = n.getArguments().toString();
                                
                                // Determine called class
                                String calledClass = n.getScope()
                                        .map(scope -> {
                                            if (scope instanceof NameExpr) {
                                                String name = ((NameExpr) scope).getNameAsString();
                                                String fullName = importMap.get(name);
                                                if (fullName != null) {
                                                    return fullName;
                                                }
                                                // Check if the class is in the same package
                                                if (classCache.containsKey(packageName + "." + name)) {
                                                    return packageName + "." + name;
                                                }
                                                // Check if the class is in the imports
                                                for (String importedClass : importMap.values()) {
                                                    if (importedClass.endsWith("." + name)) {
                                                        return importedClass;
                                                    }
                                                }
                                                // Default to the current package
                                                return packageName + "." + name;
                                            }
                                            return classKey;
                                        })
                                        .orElse(classKey);

                                // Store the called method first if it doesn't exist in the cache
                                String calledMethodKey = calledClass + "." + calledMethod + "[" + calledParameters + "]";
                                if (!methodCache.containsKey(calledMethodKey)) {
                                    if (!classCache.containsKey(calledClass)) {
                                        String packageName = calledClass.substring(0, calledClass.lastIndexOf('.'));
                                        String className = calledClass.substring(calledClass.lastIndexOf('.') + 1);
                                        storeClass(packageName, className, "", filePath.toString(), false, false, null);
                                    }
                                    
                                    storeMethod(calledClass, calledMethod, "void", calledParameters, calledParameters,
                                            false, true, false, false, false, false);
                                }

                                // Store the method call with context
                                storeMethodCall(
                                    callerClass, callerMethod, callerParameters,
                                    calledClass, calledMethod, calledParameters,
                                    n.getBegin().get().line, "this", "direct",
                                    inTryBlock, inCatchBlock, inFinallyBlock,
                                    inLoop, loopType, inConditional, conditionalType,
                                    filePath.toString()
                                );
                            }
                        }, null);
                    });
                }

                @Override
                public void visit(ConstructorDeclaration n, Void arg) {
                    methodStack.push(new MethodDeclaration());
                    super.visit(n, arg);
                    methodStack.pop();

                    String methodName = n.getNameAsString();
                    String returnType = "void";
                    String parameters = n.getParameters().toString();
                    List<String> paramTypes = new ArrayList<>();
                    for (Parameter parameter : n.getParameters()) {
                        paramTypes.add(parameter.getType().asString());
                    }
                    String parameterTypes = String.join(",", paramTypes);
                    boolean isStatic = false;
                    boolean isPublic = n.isPublic();
                    boolean isConstructor = true;
                    boolean isInitializer = false;
                    boolean isLambda = false;
                    boolean isAnonymous = n.getParentNode()
                            .map(p -> p instanceof ObjectCreationExpr)
                            .orElse(false);

                    String classKey = packageName + "." + n.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString)
                            .orElse("");

                    storeMethod(classKey, methodName, returnType, parameters, parameterTypes, 
                            isStatic, isPublic, isConstructor, isInitializer, isLambda, isAnonymous);
                }

                @Override
                public void visit(InitializerDeclaration n, Void arg) {
                    methodStack.push(new MethodDeclaration());
                    super.visit(n, arg);
                    methodStack.pop();

                    String methodName = n.isStatic() ? "<clinit>" : "<init>";
                    String returnType = "void";
                    String parameters = "()";
                    String parameterTypes = "";
                    boolean isStatic = n.isStatic();
                    boolean isPublic = true;
                    boolean isConstructor = false;
                    boolean isInitializer = true;
                    boolean isLambda = false;
                    boolean isAnonymous = false;

                    String classKey = packageName + "." + n.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString)
                            .orElse("");

                    storeMethod(classKey, methodName, returnType, parameters, parameterTypes, 
                            isStatic, isPublic, isConstructor, isInitializer, isLambda, isAnonymous);
                }

                @Override
                public void visit(LambdaExpr n, Void arg) {
                    methodStack.push(new MethodDeclaration());
                    super.visit(n, arg);
                    methodStack.pop();

                    String methodName = "lambda$" + methodStack.peek().getNameAsString();
                    String returnType = n.calculateResolvedType().describe();
                    String parameters = n.getParameters().toString();
                    List<String> paramTypes = new ArrayList<>();
                    for (Parameter parameter : n.getParameters()) {
                        paramTypes.add(parameter.getType().asString());
                    }
                    String parameterTypes = String.join(",", paramTypes);
                    boolean isStatic = false;
                    boolean isPublic = true;
                    boolean isConstructor = false;
                    boolean isInitializer = false;
                    boolean isLambda = true;
                    boolean isAnonymous = false;

                    String classKey = packageName + "." + n.findAncestor(ClassOrInterfaceDeclaration.class)
                            .map(ClassOrInterfaceDeclaration::getNameAsString)
                            .orElse("");

                    storeMethod(classKey, methodName, returnType, parameters, parameterTypes, 
                            isStatic, isPublic, isConstructor, isInitializer, isLambda, isAnonymous);
                }
            }, null);
        }
    }

    protected void storeClass(String packageName, String className, String sourceCode, 
                          String filePath, boolean isAnonymous, boolean isNested, 
                          Integer parentClassId) {
        try {
            String classKey = packageName + "." + className;
            
            // Check if class is already in cache
            if (classCache.containsKey(classKey)) {
                return;
            }
            
            // Store class in database
            int classId = dbManager.storeClass(packageName, className);
            if (classId > 0) {
                classCache.put(classKey, classId);
                logger.debug("Stored class in cache: " + classKey + " -> " + classId);
            } else {
                logger.warn("Failed to store class: " + classKey);
            }
        } catch (SQLException e) {
            logger.error("Failed to store class: " + className, e);
        }
    }

    protected void storeMethod(String classKey, String methodName, String returnType, 
                           String parameters, String parameterTypes, boolean isStatic, 
                           boolean isPublic, boolean isConstructor, boolean isInitializer,
                           boolean isLambda, boolean isAnonymous) {
        try {
            // Format parameters with spaces after commas
            String formattedParameters = parameters.replaceAll(",", ", ");
            String methodKey = classKey + "." + methodName + "[" + formattedParameters + "]";
            
            // Check if method is already in cache
            if (methodCache.containsKey(methodKey)) {
                return;
            }
            
            // Ensure class exists in cache
            Integer classId = classCache.get(classKey);
            if (classId == null) {
                logger.warn("Class not found in cache: " + classKey);
                return;
            }
            
            // Store method in database
            int methodId = dbManager.storeMethod(classId, methodName, returnType, formattedParameters, isStatic, isPublic);
            if (methodId > 0) {
                methodCache.put(methodKey, methodId);
                logger.debug("Stored method in cache: " + methodKey + " -> " + methodId);
            } else {
                logger.warn("Failed to store method: " + methodKey);
            }
        } catch (SQLException e) {
            logger.error("Failed to store method: " + methodName, e);
        }
    }

    protected void storeMethodCall(String callerClass, String callerMethod, String callerParameters,
            String calledClass, String calledMethod, String calledParameters,
            int lineNumber, String scope, String callContext,
            boolean isInTryBlock, boolean isInCatchBlock, boolean isInFinallyBlock,
            boolean isInLoop, String loopType, boolean isInConditional, String conditionalType,
            String filePath) {
        try {
            // Get caller method ID
            String callerMethodKey = callerClass + "." + callerMethod + "[" + callerParameters + "]";
            Integer callerMethodId = methodCache.get(callerMethodKey);
            if (callerMethodId == null) {
                logger.warn("Caller method not found in cache: " + callerMethodKey);
                return;
            }

            // Get called method ID
            String calledMethodKey = calledClass + "." + calledMethod + "[" + calledParameters + "]";
            Integer calledMethodId = methodCache.get(calledMethodKey);
            if (calledMethodId == null) {
                // Try to store the called method if it's not in the cache
                if (!classCache.containsKey(calledClass)) {
                    String packageName = calledClass.substring(0, calledClass.lastIndexOf('.'));
                    String className = calledClass.substring(calledClass.lastIndexOf('.') + 1);
                    storeClass(packageName, className, "", filePath, false, false, null);
                }
                storeMethod(calledClass, calledMethod, "void", calledParameters, calledParameters,
                        false, true, false, false, false, false);
                calledMethodId = methodCache.get(calledMethodKey);
                if (calledMethodId == null) {
                    logger.warn("Failed to store called method: " + calledMethodKey);
                    return;
                }
            }

            // Store method call in database
            dbManager.storeMethodCall(callerMethodId, calledMethodId, lineNumber, scope, callContext,
                    isInTryBlock, isInCatchBlock, isInFinallyBlock, isInLoop, loopType,
                    isInConditional, conditionalType);
        } catch (SQLException e) {
            logger.error("Failed to store method call", e);
        }
    }
} 