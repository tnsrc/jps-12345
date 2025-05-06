package com.jps.analysis.query;

import com.jps.analysis.db.DatabaseManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.Statement;
import static org.junit.jupiter.api.Assertions.*;

class MethodQueryTest {
    private MethodQuery methodQuery;
    private DatabaseManager dbManager;
    private static final String TEST_DB = "test_analysis.db";

    @BeforeEach
    void setUp() {
        // Set up test database
        System.setProperty("db.url", "jdbc:sqlite:" + TEST_DB);
        dbManager = DatabaseManager.getInstance();
        dbManager.createTables();
        methodQuery = new MethodQuery();
        
        // Insert test data
        try {
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            
            // Insert test classes
            stmt.executeUpdate("INSERT INTO classes (package_name, class_name) VALUES " +
                             "('com.example.test', 'TestClass'), " +
                             "('com.example.test', 'Helper')");
            
            // Insert test methods
            stmt.executeUpdate("INSERT INTO methods (class_id, method_name, return_type, parameters, is_static, is_public) " +
                             "SELECT id, 'testMethod', 'void', '[]', false, true FROM classes WHERE class_name = 'TestClass'");
            stmt.executeUpdate("INSERT INTO methods (class_id, method_name, return_type, parameters, is_static, is_public) " +
                             "SELECT id, 'helperMethod', 'void', '[]', false, true FROM classes WHERE class_name = 'Helper'");
            
            // Insert test method calls
            stmt.executeUpdate("INSERT INTO method_calls (caller_method_id, called_method_id, line_number, scope, call_context, " +
                             "is_in_try_block, is_in_catch_block, is_in_finally_block, is_in_loop, loop_type, " +
                             "is_in_conditional, conditional_type) " +
                             "SELECT m1.id, m2.id, 42, 'this', 'direct', false, false, false, false, '', false, '' " +
                             "FROM methods m1, methods m2 " +
                             "WHERE m1.method_name = 'testMethod' AND m2.method_name = 'helperMethod'");
            
            stmt.close();
        } catch (Exception e) {
            fail("Failed to set up test data: " + e.getMessage());
        }
    }

    @AfterEach
    void tearDown() {
        try {
            // Close database connection
            dbManager.close();
            
            // Delete test database
            java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(TEST_DB));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    void testFindMethodCalls() {
        // Test finding method calls from a specific method
        var calls = methodQuery.findMethodCalls("TestClass", "testMethod", "[]");
        assertFalse(calls.isEmpty(), "Should find method calls");
        assertEquals(1, calls.size(), "Should find exactly one method call");
        
        var call = calls.get(0);
        assertEquals("com.example.test.TestClass", call.getCallerClass());
        assertEquals("testMethod", call.getCallerMethod());
        assertEquals("com.example.test.Helper", call.getCalledClass());
        assertEquals("helperMethod", call.getCalledMethod());
    }

    @Test
    void testFindMethodCallsTo() {
        // Test finding method calls to a specific method
        var calls = methodQuery.findMethodCallsTo("Helper", "helperMethod", "[]");
        assertFalse(calls.isEmpty(), "Should find method calls");
        assertEquals(1, calls.size(), "Should find exactly one method call");
        
        var call = calls.get(0);
        assertEquals("com.example.test.TestClass", call.getCallerClass());
        assertEquals("testMethod", call.getCallerMethod());
        assertEquals("com.example.test.Helper", call.getCalledClass());
        assertEquals("helperMethod", call.getCalledMethod());
    }

    @Test
    void testFindMethodCallsByScope() {
        // Test finding method calls by scope
        var calls = methodQuery.findMethodCallsByScope("this");
        assertFalse(calls.isEmpty(), "Should find method calls with 'this' scope");
        
        var call = calls.get(0);
        assertEquals("this", call.getScope(), "Scope should be 'this'");
    }

    @Test
    void testFindMethodCallsInTryCatch() {
        // Test finding method calls in try-catch blocks
        var calls = methodQuery.findMethodCallsInTryCatch();
        assertTrue(calls.isEmpty(), "Should not find any method calls in try-catch blocks");
        
        // Add a method call in try-catch block
        try {
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("INSERT INTO method_calls (caller_method_id, called_method_id, line_number, scope, call_context, " +
                             "is_in_try_block, is_in_catch_block, is_in_finally_block, is_in_loop, loop_type, " +
                             "is_in_conditional, conditional_type) " +
                             "SELECT m1.id, m2.id, 42, 'this', 'direct', true, false, false, false, '', false, '' " +
                             "FROM methods m1, methods m2 " +
                             "WHERE m1.method_name = 'testMethod' AND m2.method_name = 'helperMethod'");
            stmt.close();
        } catch (Exception e) {
            fail("Failed to add test data: " + e.getMessage());
        }
        
        calls = methodQuery.findMethodCallsInTryCatch();
        assertFalse(calls.isEmpty(), "Should find method calls in try-catch blocks");
        assertTrue(calls.get(0).isInTryBlock(), "Method call should be in try block");
    }

    @Test
    void testFindMethodCallsInLoops() {
        // Test finding method calls in loops
        var calls = methodQuery.findMethodCallsInLoops();
        assertTrue(calls.isEmpty(), "Should not find any method calls in loops");
        
        // Add a method call in loop
        try {
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            stmt.executeUpdate("INSERT INTO method_calls (caller_method_id, called_method_id, line_number, scope, call_context, " +
                             "is_in_try_block, is_in_catch_block, is_in_finally_block, is_in_loop, loop_type, " +
                             "is_in_conditional, conditional_type) " +
                             "SELECT m1.id, m2.id, 42, 'this', 'direct', false, false, false, true, 'for', false, '' " +
                             "FROM methods m1, methods m2 " +
                             "WHERE m1.method_name = 'testMethod' AND m2.method_name = 'helperMethod'");
            stmt.close();
        } catch (Exception e) {
            fail("Failed to add test data: " + e.getMessage());
        }
        
        calls = methodQuery.findMethodCallsInLoops();
        assertFalse(calls.isEmpty(), "Should find method calls in loops");
        assertTrue(calls.get(0).isInLoop(), "Method call should be in loop");
        assertEquals("for", calls.get(0).getLoopType(), "Loop type should be 'for'");
    }
} 