package com.jps.analysis.query;

import com.jps.analysis.db.DatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class MethodQuery {
    private static final Logger logger = LoggerFactory.getLogger(MethodQuery.class);
    private final DatabaseManager dbManager;

    public MethodQuery() {
        this.dbManager = DatabaseManager.getInstance();
    }

    public List<MethodInfo> findMethodsBySignature(String methodName, String returnType, String parameters) {
        List<MethodInfo> methods = new ArrayList<>();
        String sql = "SELECT m.id, c.package_name, c.class_name, m.method_name, m.return_type, " +
                "m.parameters, m.is_static, m.is_public " +
                "FROM methods m " +
                "JOIN classes c ON m.class_id = c.id " +
                "WHERE m.method_name LIKE ? " +
                "AND m.return_type LIKE ? " +
                "AND m.parameters LIKE ?";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + methodName + "%");
            stmt.setString(2, "%" + returnType + "%");
            stmt.setString(3, "%" + parameters + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    methods.add(new MethodInfo(
                            rs.getInt("id"),
                            rs.getString("package_name"),
                            rs.getString("class_name"),
                            rs.getString("method_name"),
                            rs.getString("return_type"),
                            rs.getString("parameters"),
                            rs.getBoolean("is_static"),
                            rs.getBoolean("is_public")
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to query methods", e);
        }

        return methods;
    }

    public List<MethodCall> findMethodCalls(String className, String methodName, String parameters) {
        List<MethodCall> calls = new ArrayList<>();
        String sql = "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE caller.class_name = ? AND caller_method.method_name = ? " +
                "AND (caller_method.parameters = ? OR ? = '' OR ? IS NULL)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, className);
            stmt.setString(2, methodName);
            stmt.setString(3, parameters);
            stmt.setString(4, parameters);
            stmt.setString(5, parameters);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    MethodCall call = createMethodCallFromResultSet(rs);
                    if (call != null) {
                        calls.add(call);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls", e);
        }

        return calls;
    }

    public List<MethodCall> findMethodCallsTo(String className, String methodName, String parameters) {
        List<MethodCall> calls = new ArrayList<>();
        String sql = "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE called.class_name = ? AND called_method.method_name = ? " +
                "AND (called_method.parameters = ? OR ? = '' OR ? IS NULL)";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, className);
            stmt.setString(2, methodName);
            stmt.setString(3, parameters);
            stmt.setString(4, parameters);
            stmt.setString(5, parameters);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(createMethodCallFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls to", e);
        }

        return calls;
    }

    public List<MethodCall> findMethodCallsByContext(String className, String methodName, String parameters,
                                                   Map<String, Object> contextFilters) {
        List<MethodCall> calls = new ArrayList<>();
        StringBuilder sql = new StringBuilder(
                "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE caller.class_name = ? AND caller_method.method_name = ? " +
                "AND caller_method.parameters = ?");

        List<Object> params = new ArrayList<>();
        params.add(className);
        params.add(methodName);
        params.add(parameters);

        // Add context filters
        for (Map.Entry<String, Object> filter : contextFilters.entrySet()) {
            if (filter.getValue() != null) {
                sql.append(" AND mc.").append(filter.getKey()).append(" = ?");
                params.add(filter.getValue());
            }
        }

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(createMethodCallFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls by context", e);
        }

        return calls;
    }

    public List<MethodCall> findMethodCallsByScope(String scope) {
        List<MethodCall> calls = new ArrayList<>();
        String sql = "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE mc.scope = ? OR mc.scope = 'this' OR mc.scope IS NULL";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, scope);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(createMethodCallFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls by scope", e);
        }

        return calls;
    }

    public List<MethodCall> findMethodCallsInTryCatch() {
        List<MethodCall> calls = new ArrayList<>();
        String sql = "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE mc.is_in_try_block = true OR mc.is_in_catch_block = true OR mc.is_in_finally_block = true";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(createMethodCallFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls in try-catch blocks", e);
        }

        return calls;
    }

    public List<MethodCall> findMethodCallsInLoops() {
        List<MethodCall> calls = new ArrayList<>();
        String sql = "SELECT mc.id, mc.line_number, mc.scope, mc.call_context, " +
                "mc.is_in_try_block, mc.is_in_catch_block, mc.is_in_finally_block, " +
                "mc.is_in_loop, mc.loop_type, mc.is_in_conditional, mc.conditional_type, " +
                "caller.package_name as caller_package, caller.class_name as caller_class, " +
                "caller_method.method_name as caller_method, " +
                "caller_method.parameters as caller_parameters, " +
                "called.package_name as called_package, called.class_name as called_class, " +
                "called_method.method_name as called_method, " +
                "called_method.parameters as called_parameters " +
                "FROM method_calls mc " +
                "JOIN methods caller_method ON mc.caller_method_id = caller_method.id " +
                "JOIN classes caller ON caller_method.class_id = caller.id " +
                "JOIN methods called_method ON mc.called_method_id = called_method.id " +
                "JOIN classes called ON called_method.class_id = called.id " +
                "WHERE mc.is_in_loop = true";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    calls.add(createMethodCallFromResultSet(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Failed to find method calls in loops", e);
        }

        return calls;
    }

    public void exportMethodCallsToCSV(List<MethodCall> calls, String filePath) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            // Write header
            writer.write("ID,Line Number,Caller Class,Caller Method,Caller Parameters," +
                        "Called Class,Called Method,Called Parameters,Scope,Call Context," +
                        "In Try Block,In Catch Block,In Finally Block,In Loop,Loop Type," +
                        "In Conditional,Conditional Type\n");

            // Write data
            for (MethodCall call : calls) {
                writer.write(String.format("%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%b,%b,%b,%b,%s,%b,%s\n",
                    call.getId(),
                    call.getLineNumber(),
                    escapeCSV(call.getCallerClass()),
                    escapeCSV(call.getCallerMethod()),
                    escapeCSV(call.getCallerParameters()),
                    escapeCSV(call.getCalledClass()),
                    escapeCSV(call.getCalledMethod()),
                    escapeCSV(call.getCalledParameters()),
                    escapeCSV(call.getScope()),
                    escapeCSV(call.getCallContext()),
                    call.isInTryBlock(),
                    call.isInCatchBlock(),
                    call.isInFinallyBlock(),
                    call.isInLoop(),
                    escapeCSV(call.getLoopType()),
                    call.isInConditional(),
                    escapeCSV(call.getConditionalType())
                ));
            }
        }
    }

    protected String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }

    protected MethodCall createMethodCallFromResultSet(ResultSet rs) throws SQLException {
        return new MethodCall(
            rs.getInt("id"),
            rs.getInt("line_number"),
            rs.getString("caller_package") + "." + rs.getString("caller_class"),
            rs.getString("caller_method"),
            rs.getString("caller_parameters"),
            rs.getString("called_package") + "." + rs.getString("called_class"),
            rs.getString("called_method"),
            rs.getString("called_parameters"),
            rs.getString("scope"),
            rs.getString("call_context"),
            rs.getBoolean("is_in_try_block"),
            rs.getBoolean("is_in_catch_block"),
            rs.getBoolean("is_in_finally_block"),
            rs.getBoolean("is_in_loop"),
            rs.getString("loop_type"),
            rs.getBoolean("is_in_conditional"),
            rs.getString("conditional_type")
        );
    }

    public static class MethodInfo {
        private final int id;
        private final String packageName;
        private final String className;
        private final String methodName;
        private final String returnType;
        private final String parameters;
        private final boolean isStatic;
        private final boolean isPublic;

        public MethodInfo(int id, String packageName, String className, String methodName,
                         String returnType, String parameters, boolean isStatic, boolean isPublic) {
            this.id = id;
            this.packageName = packageName;
            this.className = className;
            this.methodName = methodName;
            this.returnType = returnType;
            this.parameters = parameters;
            this.isStatic = isStatic;
            this.isPublic = isPublic;
        }

        public String getFullSignature() {
            return packageName + "." + className + "." + methodName + parameters;
        }

        // Getters
        public int getId() { return id; }
        public String getPackageName() { return packageName; }
        public String getClassName() { return className; }
        public String getMethodName() { return methodName; }
        public String getReturnType() { return returnType; }
        public String getParameters() { return parameters; }
        public boolean isStatic() { return isStatic; }
        public boolean isPublic() { return isPublic; }
    }

    public static class MethodCall {
        private final int id;
        private final int lineNumber;
        private final String callerClass;
        private final String callerMethod;
        private final String callerParameters;
        private final String calledClass;
        private final String calledMethod;
        private final String calledParameters;
        private final String scope;
        private final String callContext;
        private final boolean isInTryBlock;
        private final boolean isInCatchBlock;
        private final boolean isInFinallyBlock;
        private final boolean isInLoop;
        private final String loopType;
        private final boolean isInConditional;
        private final String conditionalType;

        public MethodCall(int id, int lineNumber, String callerClass, String callerMethod,
                         String callerParameters, String calledClass, String calledMethod,
                         String calledParameters, String scope, String callContext,
                         boolean isInTryBlock, boolean isInCatchBlock, boolean isInFinallyBlock,
                         boolean isInLoop, String loopType, boolean isInConditional,
                         String conditionalType) {
            this.id = id;
            this.lineNumber = lineNumber;
            this.callerClass = callerClass;
            this.callerMethod = callerMethod;
            this.callerParameters = callerParameters;
            this.calledClass = calledClass;
            this.calledMethod = calledMethod;
            this.calledParameters = calledParameters;
            this.scope = scope;
            this.callContext = callContext;
            this.isInTryBlock = isInTryBlock;
            this.isInCatchBlock = isInCatchBlock;
            this.isInFinallyBlock = isInFinallyBlock;
            this.isInLoop = isInLoop;
            this.loopType = loopType;
            this.isInConditional = isInConditional;
            this.conditionalType = conditionalType;
        }

        public int getId() { return id; }
        public int getLineNumber() { return lineNumber; }
        public String getCallerClass() { return callerClass; }
        public String getCallerMethod() { return callerMethod; }
        public String getCallerParameters() { return callerParameters; }
        public String getCalledClass() { return calledClass; }
        public String getCalledMethod() { return calledMethod; }
        public String getCalledParameters() { return calledParameters; }
        public String getScope() { return scope; }
        public String getCallContext() { return callContext; }
        public boolean isInTryBlock() { return isInTryBlock; }
        public boolean isInCatchBlock() { return isInCatchBlock; }
        public boolean isInFinallyBlock() { return isInFinallyBlock; }
        public boolean isInLoop() { return isInLoop; }
        public String getLoopType() { return loopType; }
        public boolean isInConditional() { return isInConditional; }
        public String getConditionalType() { return conditionalType; }

        // Legacy methods with default values
        public boolean isParameterCall() { return false; }
        public String getParameterName() { return null; }
        public boolean isOverloaded() { return false; }
        public String getOverloadSignature() { return null; }
        public boolean isInherited() { return false; }
        public int getInheritedFromClassId() { return -1; }
        public boolean isPolymorphic() { return false; }
        public String getPolymorphicType() { return null; }

        @Override
        public String toString() {
            return String.format("%s.%s -> %s.%s (line %d)", callerClass, callerMethod, calledClass, calledMethod, lineNumber);
        }
    }
} 