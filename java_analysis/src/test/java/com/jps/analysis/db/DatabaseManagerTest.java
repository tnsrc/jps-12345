package com.jps.analysis.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.ResultSet;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    private DatabaseManager dbManager;
    private static final String TEST_DB = "test_analysis.db";

    @BeforeEach
    void setUp() {
        // Set up test database
        System.setProperty("db.url", "jdbc:sqlite:" + TEST_DB);
        dbManager = DatabaseManager.getInstance();
        dbManager.createTables();
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
    void testGetConnection() {
        try {
            Connection conn = dbManager.getConnection();
            assertNotNull(conn, "Connection should not be null");
            assertFalse(conn.isClosed(), "Connection should be open");
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testCreateTables() {
        try {
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            
            // Verify classes table exists
            stmt.executeQuery("SELECT * FROM classes");
            
            // Verify methods table exists
            stmt.executeQuery("SELECT * FROM methods");
            
            // Verify method_calls table exists
            stmt.executeQuery("SELECT * FROM method_calls");
            
            stmt.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testStoreAndRetrieveClass() {
        try {
            // Store a class
            int classId = dbManager.storeClass("com.example.test", "TestClass");
            assertTrue(classId > 0, "Class ID should be positive");
            
            // Verify the class was stored
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT * FROM classes WHERE id = " + classId);
            assertTrue(rs.next(), "Should find the stored class");
            assertEquals("com.example.test", rs.getString("package_name"));
            assertEquals("TestClass", rs.getString("class_name"));
            stmt.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testStoreAndRetrieveMethod() {
        try {
            // Store a class first
            int classId = dbManager.storeClass("com.example.test", "TestClass");
            
            // Store a method
            int methodId = dbManager.storeMethod(classId, "testMethod", "void", "[]", false, true);
            assertTrue(methodId > 0, "Method ID should be positive");
            
            // Verify the method was stored
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT * FROM methods WHERE id = " + methodId);
            assertTrue(rs.next(), "Should find the stored method");
            assertEquals(classId, rs.getInt("class_id"));
            assertEquals("testMethod", rs.getString("method_name"));
            assertEquals("void", rs.getString("return_type"));
            assertEquals("[]", rs.getString("parameters"));
            assertFalse(rs.getBoolean("is_static"));
            assertTrue(rs.getBoolean("is_public"));
            stmt.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }

    @Test
    void testStoreAndRetrieveMethodCall() {
        try {
            // Store classes
            int classId1 = dbManager.storeClass("com.example.test", "TestClass");
            int classId2 = dbManager.storeClass("com.example.test", "Helper");
            
            // Store methods
            int methodId1 = dbManager.storeMethod(classId1, "testMethod", "void", "[]", false, true);
            int methodId2 = dbManager.storeMethod(classId2, "helperMethod", "void", "[]", false, true);
            
            // Store a method call
            int callId = dbManager.storeMethodCall(methodId1, methodId2, 42, "this", "direct",
                                                 false, false, false, false, "",
                                                 false, "");
            assertTrue(callId > 0, "Method call ID should be positive");
            
            // Verify the method call was stored
            Connection conn = dbManager.getConnection();
            Statement stmt = conn.createStatement();
            var rs = stmt.executeQuery("SELECT * FROM method_calls WHERE id = " + callId);
            assertTrue(rs.next(), "Should find the stored method call");
            assertEquals(methodId1, rs.getInt("caller_method_id"));
            assertEquals(methodId2, rs.getInt("called_method_id"));
            assertEquals(42, rs.getInt("line_number"));
            assertEquals("this", rs.getString("scope"));
            assertEquals("direct", rs.getString("call_context"));
            assertFalse(rs.getBoolean("is_in_try_block"));
            assertFalse(rs.getBoolean("is_in_catch_block"));
            assertFalse(rs.getBoolean("is_in_finally_block"));
            assertFalse(rs.getBoolean("is_in_loop"));
            assertEquals("", rs.getString("loop_type"));
            assertFalse(rs.getBoolean("is_in_conditional"));
            assertEquals("", rs.getString("conditional_type"));
            stmt.close();
        } catch (Exception e) {
            fail("Should not throw exception: " + e.getMessage());
        }
    }
} 