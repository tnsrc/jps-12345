package com.jps.analysis.parser;

import com.jps.analysis.db.DatabaseManager;
import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class JavaSourceParserTest {
    private static final String TEST_DB = "test_analysis.db";
    private Path testProjectDir;
    private JavaParser parser;

    @BeforeEach
    void setUp() throws Exception {
        // Set up test database
        System.setProperty("db.url", "jdbc:sqlite:" + TEST_DB);
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.createTables();
        
        // Create test project directory
        testProjectDir = Files.createTempDirectory("test-project");
        Path srcDir = testProjectDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);

        // Set up JavaParser with type solver
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());
        typeSolver.add(new JavaParserTypeSolver(testProjectDir));
        parser = new JavaParser();
        parser.getParserConfiguration().setSymbolResolver(new JavaSymbolSolver(typeSolver));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Close database connection
        DatabaseManager.getInstance().close();
        
        // Delete test database
        Files.deleteIfExists(Path.of(TEST_DB));
        
        // Delete test project directory
        Files.walk(testProjectDir)
             .sorted((a, b) -> -a.compareTo(b))
             .forEach(path -> {
                 try {
                     Files.delete(path);
                 } catch (Exception e) {
                     e.printStackTrace();
                 }
             });
    }

    @Test
    void testParseSimpleClass() {
        try {
            // Create a simple Java class
            String javaCode = "package com.example;\n\n" +
                            "public class SimpleClass {\n" +
                            "    private int value;\n\n" +
                            "    public SimpleClass(int value) {\n" +
                            "        this.value = value;\n" +
                            "    }\n\n" +
                            "    public int getValue() {\n" +
                            "        return value;\n" +
                            "    }\n" +
                            "}";
            Path testFile = testProjectDir.resolve("src/main/java/com/example/SimpleClass.java");
            Files.write(testFile, javaCode.getBytes());
            
            // Parse the class
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseJavaFile(testFile, this.parser);
            
            // Verify the class was stored
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var rs = dbManager.getConnection().createStatement().executeQuery(
                "SELECT * FROM classes WHERE class_name = 'SimpleClass'");
            assertTrue(rs.next(), "Should find the parsed class");
            assertEquals("com.example", rs.getString("package_name"));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testParseMethodWithParameters() {
        try {
            // Create a Java class with a method that has parameters
            String javaCode = "package com.example;\n\n" +
                            "public class ParameterClass {\n" +
                            "    public void processData(String name, int count, boolean flag) {\n" +
                            "        System.out.println(name + \" \" + count + \" \" + flag);\n" +
                            "    }\n" +
                            "}";
            Path testFile = testProjectDir.resolve("src/main/java/com/example/ParameterClass.java");
            Files.write(testFile, javaCode.getBytes());
            
            // Parse the class
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseJavaFile(testFile, this.parser);
            
            // Verify the method was stored with correct parameters
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var rs = dbManager.getConnection().createStatement().executeQuery(
                "SELECT * FROM methods WHERE method_name = 'processData'");
            assertTrue(rs.next(), "Should find the parsed method");
            assertEquals("void", rs.getString("return_type"));
            assertEquals("[name, count, flag]", rs.getString("parameters"));
            assertFalse(rs.getBoolean("is_static"));
            assertTrue(rs.getBoolean("is_public"));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testParseMethodCalls() {
        try {
            // Create a Java class with method calls
            String javaCode = "package com.example;\n\n" +
                            "public class CallerClass {\n" +
                            "    private Helper helper = new Helper();\n\n" +
                            "    public void process() {\n" +
                            "        helper.doSomething();\n" +
                            "        staticMethod();\n" +
                            "    }\n\n" +
                            "    private static void staticMethod() {\n" +
                            "        System.out.println(\"Static method\");\n" +
                            "    }\n" +
                            "}\n\n" +
                            "class Helper {\n" +
                            "    public void doSomething() {\n" +
                            "        System.out.println(\"Doing something\");\n" +
                            "    }\n" +
                            "}";
            Path testFile = testProjectDir.resolve("src/main/java/com/example/CallerClass.java");
            Files.write(testFile, javaCode.getBytes());
            
            // Parse the class
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseJavaFile(testFile, this.parser);
            
            // Verify the method calls were stored
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var rs = dbManager.getConnection().createStatement().executeQuery(
                "SELECT * FROM method_calls WHERE call_context = 'direct'");
            assertTrue(rs.next(), "Should find method calls");
            assertEquals("this", rs.getString("scope"));
            assertEquals("", rs.getString("loop_type"));
            assertEquals("", rs.getString("conditional_type"));
            assertFalse(rs.getBoolean("is_in_try_block"));
            assertFalse(rs.getBoolean("is_in_catch_block"));
            assertFalse(rs.getBoolean("is_in_finally_block"));
            assertFalse(rs.getBoolean("is_in_loop"));
            assertFalse(rs.getBoolean("is_in_conditional"));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testParseTryCatchBlock() {
        try {
            // Create a Java class with a try-catch block
            String javaCode = "package com.example;\n\n" +
                            "public class TryCatchClass {\n" +
                            "    public void process() {\n" +
                            "        try {\n" +
                            "            System.out.println(\"Try block\");\n" +
                            "        } catch (Exception e) {\n" +
                            "            System.out.println(\"Catch block\");\n" +
                            "        } finally {\n" +
                            "            System.out.println(\"Finally block\");\n" +
                            "        }\n" +
                            "    }\n" +
                            "}";
            Path testFile = testProjectDir.resolve("src/main/java/com/example/TryCatchClass.java");
            Files.write(testFile, javaCode.getBytes());
            
            // Parse the class
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseJavaFile(testFile, this.parser);
            
            // Verify the method calls in try-catch blocks were stored
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var rs = dbManager.getConnection().createStatement().executeQuery(
                "SELECT * FROM method_calls WHERE is_in_try_block = true OR is_in_catch_block = true OR is_in_finally_block = true");
            assertTrue(rs.next(), "Should find method calls in try-catch blocks");
            assertEquals("", rs.getString("loop_type"));
            assertEquals("", rs.getString("conditional_type"));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testParseLoop() {
        try {
            // Create a Java class with loops
            String javaCode = "package com.example;\n\n" +
                            "public class LoopClass {\n" +
                            "    public void process() {\n" +
                            "        for (int i = 0; i < 10; i++) {\n" +
                            "            System.out.println(i);\n" +
                            "        }\n\n" +
                            "        while (true) {\n" +
                            "            System.out.println(\"Infinite loop\");\n" +
                            "        }\n" +
                            "    }\n" +
                            "}";
            Path testFile = testProjectDir.resolve("src/main/java/com/example/LoopClass.java");
            Files.write(testFile, javaCode.getBytes());
            
            // Parse the class
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseJavaFile(testFile, this.parser);
            
            // Verify the method calls in loops were stored
            DatabaseManager dbManager = DatabaseManager.getInstance();
            var rs = dbManager.getConnection().createStatement().executeQuery(
                "SELECT * FROM method_calls WHERE is_in_loop = true");
            assertTrue(rs.next(), "Should find method calls in loops");
            assertEquals("for", rs.getString("loop_type"));
            assertEquals("", rs.getString("conditional_type"));
            assertFalse(rs.getBoolean("is_in_try_block"));
            assertFalse(rs.getBoolean("is_in_catch_block"));
            assertFalse(rs.getBoolean("is_in_finally_block"));
            assertTrue(rs.getBoolean("is_in_loop"));
            assertFalse(rs.getBoolean("is_in_conditional"));
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
} 