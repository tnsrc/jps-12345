package com.jps.analysis;

import com.jps.analysis.parser.JavaSourceParser;
import com.jps.analysis.query.MethodQuery;
import com.jps.analysis.query.MethodQuery.MethodCall;
import com.jps.analysis.visualization.MethodCallVisualizer;
import com.jps.analysis.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final Scanner scanner = new Scanner(System.in);
    private static final MethodQuery methodQuery = new MethodQuery();
    private static final MethodCallVisualizer visualizer = new MethodCallVisualizer();

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java -jar java-analysis.jar <project-root>");
            return;
        }

        Path projectRoot = Paths.get(args[0]);
        JavaSourceParser parser = new JavaSourceParser();
        try {
            parser.parseProject(projectRoot);
            showMainMenu();
        } catch (Exception e) {
            logger.error("Failed to analyze project", e);
            System.out.println("Error: " + e.getMessage());
        } finally {
            DatabaseManager.getInstance().close();
        }
    }

    private static void showMainMenu() {
        while (true) {
            System.out.println("\nJava Method Analysis Tool");
            System.out.println("1. Find method calls from a method");
            System.out.println("2. Find method calls to a method");
            System.out.println("3. Find method calls by scope");
            System.out.println("4. Find method calls in try-catch blocks");
            System.out.println("5. Find method calls in loops");
            System.out.println("6. Find method calls by context");
            System.out.println("7. Export method calls to CSV");
            System.out.println("8. Visualize method calls");
            System.out.println("0. Exit");
            System.out.print("Enter your choice: ");

            try {
                String input = scanner.nextLine().trim();
                int choice;
                try {
                    choice = Integer.parseInt(input);
                } catch (NumberFormatException e) {
                    System.out.println("Invalid input. Please enter a number.");
                    continue;
                }

                switch (choice) {
                    case 1:
                        findMethodCallsFrom();
                        break;
                    case 2:
                        findMethodCallsTo();
                        break;
                    case 3:
                        findMethodCallsByScope();
                        break;
                    case 4:
                        findMethodCallsInTryCatch();
                        break;
                    case 5:
                        findMethodCallsInLoops();
                        break;
                    case 6:
                        findMethodCallsByContext();
                        break;
                    case 7:
                        exportMethodCalls();
                        break;
                    case 8:
                        visualizeMethodCalls();
                        break;
                    case 0:
                        return;
                    default:
                        System.out.println("Invalid choice. Please try again.");
                }
            } catch (Exception e) {
                logger.error("Error processing choice", e);
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void findMethodCallsFrom() {
        System.out.print("Enter package name: ");
        String packageName = scanner.nextLine();
        System.out.print("Enter class name: ");
        String className = scanner.nextLine();
        System.out.print("Enter method name: ");
        String methodName = scanner.nextLine();
        System.out.print("Enter method parameters: ");
        String parameters = scanner.nextLine();

        String fullClassName = packageName + "." + className;
        List<MethodCall> calls = methodQuery.findMethodCalls(fullClassName, methodName, parameters);
        displayMethodCalls(calls);
    }

    private static void findMethodCallsTo() {
        System.out.print("Enter class name: ");
        String className = scanner.nextLine();
        System.out.print("Enter method name: ");
        String methodName = scanner.nextLine();
        System.out.print("Enter method parameters: ");
        String parameters = scanner.nextLine();

        List<MethodCall> calls = methodQuery.findMethodCallsTo(className, methodName, parameters);
        displayMethodCalls(calls);
    }

    private static void findMethodCallsByScope() {
        System.out.print("Enter scope (INSTANCE/STATIC): ");
        String scope = scanner.nextLine().toUpperCase();

        List<MethodCall> calls = methodQuery.findMethodCallsByScope(scope);
        displayMethodCalls(calls);
    }

    private static void findMethodCallsInTryCatch() {
        List<MethodCall> calls = methodQuery.findMethodCallsInTryCatch();
        displayMethodCalls(calls);
    }

    private static void findMethodCallsInLoops() {
        List<MethodCall> calls = methodQuery.findMethodCallsInLoops();
        displayMethodCalls(calls);
    }

    private static void findMethodCallsByContext() {
        System.out.print("Enter class name: ");
        String className = scanner.nextLine();
        System.out.print("Enter method name: ");
        String methodName = scanner.nextLine();
        System.out.print("Enter method parameters: ");
        String parameters = scanner.nextLine();

        Map<String, Object> contextFilters = new HashMap<>();
        contextFilters.put("is_parameter_call", false);
        contextFilters.put("is_overloaded", false);
        contextFilters.put("is_inherited", false);
        contextFilters.put("is_polymorphic", false);
        contextFilters.put("is_in_try_block", false);
        contextFilters.put("is_in_catch_block", false);
        contextFilters.put("is_in_finally_block", false);
        contextFilters.put("is_in_loop", false);
        contextFilters.put("is_in_conditional", false);

        System.out.println("Enter context filters (press Enter to skip):");
        
        System.out.print("Is parameter call (true/false): ");
        String isParameterCall = scanner.nextLine();
        if (!isParameterCall.isEmpty()) {
            contextFilters.put("is_parameter_call", Boolean.parseBoolean(isParameterCall));
        }

        System.out.print("Is overloaded (true/false): ");
        String isOverloaded = scanner.nextLine();
        if (!isOverloaded.isEmpty()) {
            contextFilters.put("is_overloaded", Boolean.parseBoolean(isOverloaded));
        }

        System.out.print("Is inherited (true/false): ");
        String isInherited = scanner.nextLine();
        if (!isInherited.isEmpty()) {
            contextFilters.put("is_inherited", Boolean.parseBoolean(isInherited));
        }

        System.out.print("Is polymorphic (true/false): ");
        String isPolymorphic = scanner.nextLine();
        if (!isPolymorphic.isEmpty()) {
            contextFilters.put("is_polymorphic", Boolean.parseBoolean(isPolymorphic));
        }

        List<MethodCall> calls = methodQuery.findMethodCallsByContext(className, methodName, parameters, contextFilters);
        displayMethodCalls(calls);
    }

    private static void exportMethodCalls() {
        System.out.print("Enter class name: ");
        String className = scanner.nextLine();
        System.out.print("Enter method name: ");
        String methodName = scanner.nextLine();
        System.out.print("Enter method parameters: ");
        String parameters = scanner.nextLine();
        System.out.print("Enter output file path: ");
        String filePath = scanner.nextLine();

        try {
            List<MethodCall> calls = methodQuery.findMethodCalls(className, methodName, parameters);
            methodQuery.exportMethodCallsToCSV(calls, filePath);
            System.out.println("Method calls exported successfully to " + filePath);
        } catch (IOException e) {
            logger.error("Failed to export method calls", e);
            System.out.println("Error exporting method calls: " + e.getMessage());
        }
    }

    private static void visualizeMethodCalls() {
        System.out.print("Enter class name: ");
        String className = scanner.nextLine();
        System.out.print("Enter method name: ");
        String methodName = scanner.nextLine();
        System.out.print("Enter method parameters: ");
        String parameters = scanner.nextLine();

        List<MethodCall> calls = methodQuery.findMethodCalls(className, methodName, parameters);
        if (calls.isEmpty()) {
            System.out.println("No method calls found to visualize.");
            return;
        }

        System.out.println("\nVisualization Options:");
        System.out.println("1. Generate Call Graph (DOT format)");
        System.out.println("2. Generate Call Tree");
        System.out.println("3. Generate Call Matrix");
        System.out.println("4. Generate Call Statistics");
        System.out.println("5. Generate Sequence Diagram (PlantUML)");
        System.out.println("6. Generate Dependency Graph (DOT format)");
        System.out.println("7. Generate Class Hierarchy (DOT format)");
        System.out.println("8. Generate Method Complexity Graph (DOT format)");
        System.out.println("9. Generate Interactive Visualization (HTML)");
        System.out.print("Enter your choice: ");

        int choice = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        System.out.print("Enter output file path: ");
        String outputPath = scanner.nextLine();

        try {
            switch (choice) {
                case 1:
                    visualizer.generateCallGraph(calls, outputPath);
                    System.out.println("Call graph generated in DOT format. Use Graphviz to render it.");
                    break;
                case 2:
                    visualizer.generateCallTree(calls, outputPath);
                    System.out.println("Call tree generated successfully.");
                    break;
                case 3:
                    visualizer.generateCallMatrix(calls, outputPath);
                    System.out.println("Call matrix generated successfully.");
                    break;
                case 4:
                    visualizer.generateCallStatistics(calls, outputPath);
                    System.out.println("Call statistics generated successfully.");
                    break;
                case 5:
                    visualizer.generateSequenceDiagram(calls, outputPath);
                    System.out.println("Sequence diagram generated in PlantUML format. Use PlantUML to render it.");
                    break;
                case 6:
                    visualizer.generateDependencyGraph(calls, outputPath);
                    System.out.println("Dependency graph generated in DOT format. Use Graphviz to render it.");
                    break;
                case 7:
                    visualizer.generateClassHierarchy(calls, outputPath);
                    System.out.println("Class hierarchy generated in DOT format. Use Graphviz to render it.");
                    break;
                case 8:
                    visualizer.generateMethodComplexityGraph(calls, outputPath);
                    System.out.println("Method complexity graph generated in DOT format. Use Graphviz to render it.");
                    break;
                case 9:
                    visualizer.generateInteractiveVisualization(calls, outputPath);
                    System.out.println("Interactive visualization generated in HTML format. Open the file in a web browser to view it.");
                    break;
                default:
                    System.out.println("Invalid choice.");
                    return;
            }
            System.out.println("Output saved to: " + outputPath);
        } catch (IOException e) {
            logger.error("Failed to generate visualization", e);
            System.out.println("Error generating visualization: " + e.getMessage());
        }
    }

    private static void displayMethodCalls(List<MethodCall> calls) {
        if (calls.isEmpty()) {
            System.out.println("No method calls found.");
            return;
        }

        System.out.println("\nFound " + calls.size() + " method calls:");
        for (MethodCall call : calls) {
            System.out.println("\nMethod Call Details:");
            System.out.println("  ID: " + call.getId());
            System.out.println("  Line Number: " + call.getLineNumber());
            System.out.println("  Caller: " + call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters());
            System.out.println("  Called: " + call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters());
            System.out.println("  Scope: " + call.getScope());
            System.out.println("  Call Context: " + call.getCallContext());
            
            if (call.isParameterCall()) {
                System.out.println("  Parameter Call: Yes");
                System.out.println("  Parameter Name: " + call.getParameterName());
            }
            
            if (call.isOverloaded()) {
                System.out.println("  Overloaded: Yes");
                System.out.println("  Overload Signature: " + call.getOverloadSignature());
            }
            
            if (call.isInherited()) {
                System.out.println("  Inherited: Yes");
                System.out.println("  Inherited From Class ID: " + call.getInheritedFromClassId());
            }
            
            if (call.isPolymorphic()) {
                System.out.println("  Polymorphic: Yes");
                System.out.println("  Polymorphic Type: " + call.getPolymorphicType());
            }
            
            if (call.isInTryBlock() || call.isInCatchBlock() || call.isInFinallyBlock()) {
                System.out.println("  Exception Handling Context:");
                System.out.println("    In Try Block: " + call.isInTryBlock());
                System.out.println("    In Catch Block: " + call.isInCatchBlock());
                System.out.println("    In Finally Block: " + call.isInFinallyBlock());
            }
            
            if (call.isInLoop()) {
                System.out.println("  Loop Context:");
                System.out.println("    Loop Type: " + call.getLoopType());
            }
            
            if (call.isInConditional()) {
                System.out.println("  Conditional Context:");
                System.out.println("    Conditional Type: " + call.getConditionalType());
            }
        }
    }
} 