package com.jps.analysis.db;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private static final String DEFAULT_DB_URL = "jdbc:sqlite:java_analysis.db";
    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        // Don't create connection in constructor
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    private String getDbUrl() {
        return System.getProperty("db.url", DEFAULT_DB_URL);
    }

    public Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            connection = DriverManager.getConnection(getDbUrl());
            createTables(); // Create tables when connection is first established
        }
        return connection;
    }

    public void createTables() {
        try (Statement stmt = getConnection().createStatement()) {
            // Create classes table
            stmt.execute("CREATE TABLE IF NOT EXISTS classes (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "package_name TEXT NOT NULL, " +
                        "class_name TEXT NOT NULL, " +
                        "UNIQUE(package_name, class_name))");
            
            // Create methods table
            stmt.execute("CREATE TABLE IF NOT EXISTS methods (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "class_id INTEGER NOT NULL, " +
                        "method_name TEXT NOT NULL, " +
                        "return_type TEXT NOT NULL, " +
                        "parameters TEXT NOT NULL, " +
                        "is_static BOOLEAN NOT NULL, " +
                        "is_public BOOLEAN NOT NULL, " +
                        "FOREIGN KEY(class_id) REFERENCES classes(id), " +
                        "UNIQUE(class_id, method_name, parameters))");
            
            // Create method_calls table
            stmt.execute("CREATE TABLE IF NOT EXISTS method_calls (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "caller_method_id INTEGER NOT NULL, " +
                        "called_method_id INTEGER NOT NULL, " +
                        "line_number INTEGER NOT NULL, " +
                        "scope TEXT NOT NULL, " +
                        "call_context TEXT NOT NULL, " +
                        "is_in_try_block BOOLEAN NOT NULL DEFAULT false, " +
                        "is_in_catch_block BOOLEAN NOT NULL DEFAULT false, " +
                        "is_in_finally_block BOOLEAN NOT NULL DEFAULT false, " +
                        "is_in_loop BOOLEAN NOT NULL DEFAULT false, " +
                        "loop_type TEXT, " +
                        "is_in_conditional BOOLEAN NOT NULL DEFAULT false, " +
                        "conditional_type TEXT, " +
                        "FOREIGN KEY(caller_method_id) REFERENCES methods(id), " +
                        "FOREIGN KEY(called_method_id) REFERENCES methods(id))");
        } catch (SQLException e) {
            throw new RuntimeException("Failed to create database tables", e);
        }
    }

    public int storeClass(String packageName, String className) throws SQLException {
        String sql = "INSERT INTO classes (package_name, class_name) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, packageName);
            stmt.setString(2, className);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public int storeMethod(int classId, String methodName, String returnType, String parameters, boolean isStatic, boolean isPublic) throws SQLException {
        String sql = "INSERT INTO methods (class_id, method_name, return_type, parameters, is_static, is_public) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, classId);
            stmt.setString(2, methodName);
            stmt.setString(3, returnType);
            stmt.setString(4, parameters);
            stmt.setBoolean(5, isStatic);
            stmt.setBoolean(6, isPublic);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public int storeMethodCall(int callerMethodId, int calledMethodId, int lineNumber, String scope, String callContext,
                             boolean isInTryBlock, boolean isInCatchBlock, boolean isInFinallyBlock, boolean isInLoop,
                             String loopType, boolean isInConditional, String conditionalType) throws SQLException {
        String sql = "INSERT INTO method_calls (caller_method_id, called_method_id, line_number, scope, call_context, " +
                    "is_in_try_block, is_in_catch_block, is_in_finally_block, is_in_loop, loop_type, " +
                    "is_in_conditional, conditional_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, callerMethodId);
            stmt.setInt(2, calledMethodId);
            stmt.setInt(3, lineNumber);
            stmt.setString(4, scope);
            stmt.setString(5, callContext);
            stmt.setBoolean(6, isInTryBlock);
            stmt.setBoolean(7, isInCatchBlock);
            stmt.setBoolean(8, isInFinallyBlock);
            stmt.setBoolean(9, isInLoop);
            stmt.setString(10, loopType);
            stmt.setBoolean(11, isInConditional);
            stmt.setString(12, conditionalType);
            stmt.executeUpdate();
            try (ResultSet rs = stmt.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        return -1;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
            }
        }
    }
} 