package com.jps.analysis.cli;

import com.jps.analysis.db.DatabaseManager;
import com.jps.analysis.parser.JavaSourceParser;
import com.jps.analysis.query.MethodQuery;
import com.github.javaparser.JavaParser;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        try {
            // Initialize database
            DatabaseManager dbManager = DatabaseManager.getInstance();
            dbManager.createTables();

            // Parse Java project
            if (args.length > 0) {
                Path projectRoot = Paths.get(args[0]);
                System.out.println("Parsing Java files in " + projectRoot);
                
                JavaSourceParser parser = new JavaSourceParser();
                parser.parseProject(projectRoot);
                
                // Find method calls
                MethodQuery methodQuery = new MethodQuery();
                List<MethodQuery.MethodCall> calls = methodQuery.findMethodCalls("TestClass", "testMethod", "[]");
                if (!calls.isEmpty()) {
                    System.out.println("Method calls from TestClass.testMethod:");
                    for (MethodQuery.MethodCall call : calls) {
                        System.out.println(call.getCalledClass() + "." + call.getCalledMethod());
                    }
                } else {
                    System.out.println("No method calls found from TestClass.testMethod");
                }
                
                // Find method calls by scope
                calls = methodQuery.findMethodCallsByScope("this");
                if (!calls.isEmpty()) {
                    System.out.println("Method calls with scope: this");
                    for (MethodQuery.MethodCall call : calls) {
                        System.out.println(call.getCalledClass() + "." + call.getCalledMethod());
                    }
                } else {
                    System.out.println("No method calls found with scope: this");
                }
            } else {
                System.out.println("Please provide the project root directory as an argument");
            }
        } catch (Exception e) {
            logger.error("Error: ", e);
            System.exit(1);
        }
    }
} 