package com.jps.analysis.cli;

import com.jps.analysis.db.DatabaseManager;
import com.jps.analysis.parser.JavaSourceParser;
import com.jps.analysis.query.MethodQuery;
import com.github.javaparser.JavaParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private static final String TEST_DB = "test_analysis.db";
    private Path testProjectDir;

    @BeforeEach
    void setUp() throws Exception {
        // Redirect System.out
        System.setOut(new PrintStream(outContent));
        
        // Set up test database
        System.setProperty("db.url", "jdbc:sqlite:" + TEST_DB);
        DatabaseManager dbManager = DatabaseManager.getInstance();
        dbManager.createTables();
        
        // Create test project directory
        testProjectDir = Files.createTempDirectory("test-project");
        Path srcDir = testProjectDir.resolve("src/main/java/com/example");
        Files.createDirectories(srcDir);
        
        // Create test Java file
        String javaCode = "package com.example;\n\n" +
                         "public class TestClass {\n" +
                         "    public void testMethod() {\n" +
                         "        Helper.helperMethod();\n" +
                         "    }\n" +
                         "}\n\n" +
                         "class Helper {\n" +
                         "    public static void helperMethod() {\n" +
                         "        System.out.println(\"Hello\");\n" +
                         "    }\n" +
                         "}";
        Files.write(srcDir.resolve("TestClass.java"), javaCode.getBytes());
    }

    @AfterEach
    void tearDown() throws Exception {
        // Restore System.out
        System.setOut(originalOut);
        
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
    void testParseProject() {
        try {
            // Test parsing a Java project
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseProject(testProjectDir);
            
            String output = outContent.toString();
            assertTrue(output.contains("Parsing Java files in"), "Should show parsing message");
            assertTrue(output.contains("Found class: com.example.TestClass"), "Should find TestClass");
            assertTrue(output.contains("Found class: com.example.Helper"), "Should find Helper");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testFindMethodCalls() {
        try {
            // First parse the project
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseProject(testProjectDir);
            
            // Clear output buffer
            outContent.reset();
            
            // Test finding method calls
            MethodQuery methodQuery = new MethodQuery();
            var calls = methodQuery.findMethodCalls("TestClass", "testMethod", "[]");
            
            String output = outContent.toString();
            assertTrue(output.contains("Method calls from TestClass.testMethod"), "Should show method calls");
            assertTrue(output.contains("Helper.helperMethod"), "Should find helper method call");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testFindMethodCallsByScope() {
        try {
            // First parse the project
            JavaSourceParser parser = new JavaSourceParser();
            parser.parseProject(testProjectDir);
            
            // Clear output buffer
            outContent.reset();
            
            // Test finding method calls by scope
            MethodQuery methodQuery = new MethodQuery();
            var calls = methodQuery.findMethodCallsByScope("this");
            
            String output = outContent.toString();
            assertTrue(output.contains("Method calls with scope: this"), "Should show scope message");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
} 