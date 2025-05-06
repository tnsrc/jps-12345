package com.jps.analysis.visualization;

import com.jps.analysis.query.MethodQuery.MethodCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;
import org.apache.batik.transcoder.SVGAbstractTranscoder;
import org.apache.fop.svg.PDFTranscoder;

public class MethodCallVisualizer {
    private static final Logger logger = LoggerFactory.getLogger(MethodCallVisualizer.class);

    public void generateCallGraph(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder dotContent = new StringBuilder();
        dotContent.append("digraph MethodCalls {\n");
        dotContent.append("  node [shape=box, style=filled, fillcolor=lightblue];\n");
        dotContent.append("  edge [color=gray50];\n\n");

        // Group calls by caller
        Map<String, List<MethodCall>> callsByCaller = calls.stream()
                .collect(Collectors.groupingBy(call -> 
                    call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters()));

        // Add nodes and edges
        for (Map.Entry<String, List<MethodCall>> entry : callsByCaller.entrySet()) {
            String caller = entry.getKey();
            List<MethodCall> callees = entry.getValue();

            // Add caller node
            dotContent.append(String.format("  \"%s\" [label=\"%s\"];\n", 
                escapeDot(caller), escapeDot(caller)));

            // Add edges to callees
            for (MethodCall call : callees) {
                String callee = call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters();
                String edgeLabel = String.format("[%d] %s", call.getLineNumber(), call.getScope());
                
                // Add callee node
                dotContent.append(String.format("  \"%s\" [label=\"%s\"];\n", 
                    escapeDot(callee), escapeDot(callee)));
                
                // Add edge
                dotContent.append(String.format("  \"%s\" -> \"%s\" [label=\"%s\"];\n", 
                    escapeDot(caller), escapeDot(callee), edgeLabel));
            }
        }

        dotContent.append("}\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(dotContent.toString());
        }
    }

    public void generateCallTree(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder treeContent = new StringBuilder();
        treeContent.append("Method Call Tree\n");
        treeContent.append("===============\n\n");

        // Group calls by caller
        Map<String, List<MethodCall>> callsByCaller = calls.stream()
                .collect(Collectors.groupingBy(call -> 
                    call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters()));

        // Generate tree structure
        for (Map.Entry<String, List<MethodCall>> entry : callsByCaller.entrySet()) {
            String caller = entry.getKey();
            List<MethodCall> callees = entry.getValue();

            treeContent.append(caller).append("\n");
            for (MethodCall call : callees) {
                String callee = call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters();
                treeContent.append("  └─ ").append(callee)
                    .append(" (Line: ").append(call.getLineNumber())
                    .append(", Scope: ").append(call.getScope())
                    .append(")\n");
            }
            treeContent.append("\n");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(treeContent.toString());
        }
    }

    public void generateCallMatrix(List<MethodCall> calls, String outputPath) throws IOException {
        // Get unique methods
        List<String> methods = calls.stream()
                .flatMap(call -> List.of(
                    call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters(),
                    call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters()
                ).stream())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Create matrix
        int size = methods.size();
        int[][] matrix = new int[size][size];
        Map<String, Integer> methodIndex = new HashMap<>();
        for (int i = 0; i < size; i++) {
            methodIndex.put(methods.get(i), i);
        }

        // Fill matrix
        for (MethodCall call : calls) {
            String caller = call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters();
            String callee = call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters();
            matrix[methodIndex.get(caller)][methodIndex.get(callee)]++;
        }

        // Generate matrix content
        StringBuilder matrixContent = new StringBuilder();
        matrixContent.append("Method Call Matrix\n");
        matrixContent.append("=================\n\n");

        // Header
        matrixContent.append(String.format("%-40s", "Method"));
        for (String method : methods) {
            matrixContent.append(String.format("%-10s", method.substring(0, Math.min(10, method.length()))));
        }
        matrixContent.append("\n");

        // Rows
        for (int i = 0; i < size; i++) {
            matrixContent.append(String.format("%-40s", methods.get(i)));
            for (int j = 0; j < size; j++) {
                matrixContent.append(String.format("%-10d", matrix[i][j]));
            }
            matrixContent.append("\n");
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(matrixContent.toString());
        }
    }

    public void generateCallStatistics(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder statsContent = new StringBuilder();
        statsContent.append("Method Call Statistics\n");
        statsContent.append("====================\n\n");

        // Total calls
        statsContent.append("Total Method Calls: ").append(calls.size()).append("\n\n");

        // Calls by scope
        Map<String, Long> callsByScope = calls.stream()
                .collect(Collectors.groupingBy(MethodCall::getScope, Collectors.counting()));
        statsContent.append("Calls by Scope:\n");
        callsByScope.forEach((scope, count) -> 
            statsContent.append(String.format("  %-10s: %d\n", scope, count)));
        statsContent.append("\n");

        // Calls by context
        Map<String, Long> callsByContext = calls.stream()
                .collect(Collectors.groupingBy(MethodCall::getCallContext, Collectors.counting()));
        statsContent.append("Calls by Context:\n");
        callsByContext.forEach((context, count) -> 
            statsContent.append(String.format("  %-10s: %d\n", context, count)));
        statsContent.append("\n");

        // Most called methods
        Map<String, Long> mostCalled = calls.stream()
                .collect(Collectors.groupingBy(call -> 
                    call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters(),
                    Collectors.counting()));
        statsContent.append("Most Called Methods:\n");
        mostCalled.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(entry -> 
                    statsContent.append(String.format("  %-50s: %d\n", entry.getKey(), entry.getValue())));
        statsContent.append("\n");

        // Methods with most calls
        Map<String, Long> mostCalling = calls.stream()
                .collect(Collectors.groupingBy(call -> 
                    call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters(),
                    Collectors.counting()));
        statsContent.append("Methods with Most Calls:\n");
        mostCalling.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .forEach(entry -> 
                    statsContent.append(String.format("  %-50s: %d\n", entry.getKey(), entry.getValue())));

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(statsContent.toString());
        }
    }

    public void generateSequenceDiagram(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder diagramContent = new StringBuilder();
        diagramContent.append("@startuml\n");
        diagramContent.append("skinparam sequence {\n");
        diagramContent.append("  ArrowColor Black\n");
        diagramContent.append("  ActorBorderColor Black\n");
        diagramContent.append("  LifeLineBorderColor Black\n");
        diagramContent.append("  ParticipantBorderColor Black\n");
        diagramContent.append("  ParticipantBackgroundColor LightBlue\n");
        diagramContent.append("}\n\n");

        // Group calls by caller class
        Map<String, List<MethodCall>> callsByClass = calls.stream()
                .collect(Collectors.groupingBy(MethodCall::getCallerClass));

        // Add participants
        Set<String> classes = new TreeSet<>();
        calls.forEach(call -> {
            classes.add(call.getCallerClass());
            classes.add(call.getCalledClass());
        });
        classes.forEach(className -> 
            diagramContent.append("participant \"").append(className).append("\"\n"));

        // Add method calls
        for (Map.Entry<String, List<MethodCall>> entry : callsByClass.entrySet()) {
            String callerClass = entry.getKey();
            List<MethodCall> classCalls = entry.getValue();

            // Sort calls by line number to maintain sequence
            classCalls.sort(Comparator.comparingInt(MethodCall::getLineNumber));

            for (MethodCall call : classCalls) {
                String calledClass = call.getCalledClass();
                String methodName = call.getCalledMethod() + call.getCalledParameters();
                String scope = call.getScope();
                String context = getCallContext(call);

                diagramContent.append("\"")
                    .append(callerClass)
                    .append("\" -> \"")
                    .append(calledClass)
                    .append("\": ")
                    .append(methodName)
                    .append(" (")
                    .append(scope)
                    .append(context.isEmpty() ? "" : ", " + context)
                    .append(")\n");
            }
        }

        diagramContent.append("@enduml\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(diagramContent.toString());
        }
    }

    public void generateDependencyGraph(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder dotContent = new StringBuilder();
        dotContent.append("digraph Dependencies {\n");
        dotContent.append("  node [shape=box, style=filled, fillcolor=lightblue];\n");
        dotContent.append("  edge [color=gray50, dir=forward];\n\n");

        // Group calls by class
        Map<String, Set<String>> dependencies = new HashMap<>();
        Set<String> allClasses = new TreeSet<>();

        // Collect all classes and their dependencies
        for (MethodCall call : calls) {
            String callerClass = call.getCallerClass();
            String calledClass = call.getCalledClass();
            
            allClasses.add(callerClass);
            allClasses.add(calledClass);
            
            dependencies.computeIfAbsent(callerClass, k -> new HashSet<>()).add(calledClass);
        }

        // Add nodes with method count
        Map<String, Long> methodCounts = calls.stream()
                .collect(Collectors.groupingBy(
                    call -> call.getCallerClass() + " -> " + call.getCalledClass(),
                    Collectors.counting()
                ));

        // Add nodes
        for (String className : allClasses) {
            long outgoingCalls = dependencies.getOrDefault(className, Collections.emptySet()).size();
            dotContent.append(String.format("  \"%s\" [label=\"%s\\n%d methods\"];\n", 
                escapeDot(className), escapeDot(className), outgoingCalls));
        }

        // Add edges with call count
        for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
            String callerClass = entry.getKey();
            for (String calledClass : entry.getValue()) {
                String key = callerClass + " -> " + calledClass;
                long callCount = methodCounts.getOrDefault(key, 1L);
                dotContent.append(String.format("  \"%s\" -> \"%s\" [label=\"%d calls\"];\n", 
                    escapeDot(callerClass), escapeDot(calledClass), callCount));
            }
        }

        dotContent.append("}\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(dotContent.toString());
        }
    }

    public void generateClassHierarchy(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder dotContent = new StringBuilder();
        dotContent.append("digraph ClassHierarchy {\n");
        dotContent.append("  node [shape=box, style=filled, fillcolor=lightblue];\n");
        dotContent.append("  edge [color=gray50, dir=back];\n\n");

        // Group classes by inheritance
        Map<String, Set<String>> inheritanceMap = new HashMap<>();
        Set<String> allClasses = new TreeSet<>();

        // Collect inheritance relationships
        for (MethodCall call : calls) {
            if (call.isInherited()) {
                String childClass = call.getCallerClass();
                String parentClass = String.valueOf(call.getInheritedFromClassId());
                
                allClasses.add(childClass);
                allClasses.add(parentClass);
                
                inheritanceMap.computeIfAbsent(childClass, k -> new HashSet<>()).add(parentClass);
            }
        }

        // Add nodes with method count
        Map<String, Long> methodCounts = calls.stream()
                .collect(Collectors.groupingBy(
                    MethodCall::getCallerClass,
                    Collectors.counting()
                ));

        // Add nodes
        for (String className : allClasses) {
            long methodCount = methodCounts.getOrDefault(className, 0L);
            dotContent.append(String.format("  \"%s\" [label=\"%s\\n%d methods\"];\n", 
                escapeDot(className), escapeDot(className), methodCount));
        }

        // Add inheritance edges
        for (Map.Entry<String, Set<String>> entry : inheritanceMap.entrySet()) {
            String childClass = entry.getKey();
            for (String parentClass : entry.getValue()) {
                dotContent.append(String.format("  \"%s\" -> \"%s\" [label=\"extends\"];\n", 
                    escapeDot(childClass), escapeDot(parentClass)));
            }
        }

        dotContent.append("}\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(dotContent.toString());
        }
    }

    public void generateMethodComplexityGraph(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder dotContent = new StringBuilder();
        dotContent.append("digraph MethodComplexity {\n");
        dotContent.append("  node [shape=box, style=filled];\n");
        dotContent.append("  edge [color=gray50];\n\n");

        // Calculate complexity metrics for each method
        Map<String, MethodComplexity> complexityMap = new HashMap<>();

        for (MethodCall call : calls) {
            String methodKey = call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters();
            MethodComplexity complexity = complexityMap.computeIfAbsent(methodKey, 
                k -> new MethodComplexity(call.getCallerClass(), call.getCallerMethod(), call.getCallerParameters()));

            // Update complexity metrics
            complexity.incrementCallCount();
            if (call.isInTryBlock() || call.isInCatchBlock() || call.isInFinallyBlock()) {
                complexity.incrementExceptionHandlingCount();
            }
            if (call.isInLoop()) {
                complexity.incrementLoopCount();
            }
            if (call.isInConditional()) {
                complexity.incrementConditionalCount();
            }
        }

        // Add nodes with complexity information
        for (MethodComplexity complexity : complexityMap.values()) {
            String color = getComplexityColor(complexity.getTotalComplexity());
            dotContent.append(String.format("  \"%s\" [label=\"%s\\nCalls: %d\\nExceptions: %d\\nLoops: %d\\nConditionals: %d\", fillcolor=\"%s\"];\n", 
                escapeDot(complexity.getMethodKey()),
                escapeDot(complexity.getMethodKey()),
                complexity.getCallCount(),
                complexity.getExceptionHandlingCount(),
                complexity.getLoopCount(),
                complexity.getConditionalCount(),
                color));
        }

        // Add edges for method calls
        for (MethodCall call : calls) {
            String callerKey = call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters();
            String calledKey = call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters();
            
            dotContent.append(String.format("  \"%s\" -> \"%s\" [label=\"%d\"];\n", 
                escapeDot(callerKey),
                escapeDot(calledKey),
                call.getLineNumber()));
        }

        dotContent.append("}\n");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(dotContent.toString());
        }
    }

    private String getComplexityColor(int complexity) {
        if (complexity < 5) return "lightgreen";
        if (complexity < 10) return "yellow";
        if (complexity < 20) return "orange";
        return "red";
    }

    private static class MethodComplexity {
        private final String className;
        private final String methodName;
        private final String parameters;
        private int callCount;
        private int exceptionHandlingCount;
        private int loopCount;
        private int conditionalCount;

        public MethodComplexity(String className, String methodName, String parameters) {
            this.className = className;
            this.methodName = methodName;
            this.parameters = parameters;
        }

        public String getMethodKey() {
            return className + "." + methodName + parameters;
        }

        public int getTotalComplexity() {
            return callCount + exceptionHandlingCount + loopCount + conditionalCount;
        }

        public void incrementCallCount() { callCount++; }
        public void incrementExceptionHandlingCount() { exceptionHandlingCount++; }
        public void incrementLoopCount() { loopCount++; }
        public void incrementConditionalCount() { conditionalCount++; }

        public int getCallCount() { return callCount; }
        public int getExceptionHandlingCount() { return exceptionHandlingCount; }
        public int getLoopCount() { return loopCount; }
        public int getConditionalCount() { return conditionalCount; }
    }

    private String getCallContext(MethodCall call) {
        List<String> contexts = new ArrayList<>();
        
        if (call.isInTryBlock()) contexts.add("try");
        if (call.isInCatchBlock()) contexts.add("catch");
        if (call.isInFinallyBlock()) contexts.add("finally");
        if (call.isInLoop()) contexts.add(call.getLoopType());
        if (call.isInConditional()) contexts.add(call.getConditionalType());
        if (call.isOverloaded()) contexts.add("overloaded");
        if (call.isInherited()) contexts.add("inherited");
        if (call.isPolymorphic()) contexts.add("polymorphic");
        
        return String.join(", ", contexts);
    }

    public void generateInteractiveVisualization(List<MethodCall> calls, String outputPath) throws IOException {
        StringBuilder htmlContent = new StringBuilder();
        htmlContent.append("<!DOCTYPE html>\n");
        htmlContent.append("<html>\n");
        htmlContent.append("<head>\n");
        htmlContent.append("  <title>Method Call Visualization</title>\n");
        htmlContent.append("  <script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        htmlContent.append("  <style>\n");
        htmlContent.append("    .node { cursor: pointer; }\n");
        htmlContent.append("    .node.instance { fill: #1f77b4; }\n");
        htmlContent.append("    .node.static { fill: #ff7f0e; }\n");
        htmlContent.append("    .node.constructor { fill: #2ca02c; }\n");
        htmlContent.append("    .node.initializer { fill: #d62728; }\n");
        htmlContent.append("    .link { stroke: #999; stroke-opacity: 0.6; }\n");
        htmlContent.append("    .link.highlighted { stroke: #ff0000; stroke-width: 2px; }\n");
        htmlContent.append("    .tooltip { position: absolute; padding: 8px; background: rgba(0, 0, 0, 0.8); color: white; border-radius: 4px; }\n");
        htmlContent.append("    .controls { margin-bottom: 20px; display: flex; flex-wrap: wrap; gap: 10px; }\n");
        htmlContent.append("    .search-box { padding: 5px; border-radius: 4px; border: 1px solid #ccc; }\n");
        htmlContent.append("    .filter-box { padding: 5px; border-radius: 4px; border: 1px solid #ccc; }\n");
        htmlContent.append("    .zoom-controls { position: absolute; top: 10px; right: 10px; }\n");
        htmlContent.append("    .zoom-button { margin-left: 5px; padding: 5px 10px; border-radius: 4px; border: 1px solid #ccc; background: white; cursor: pointer; }\n");
        htmlContent.append("    .path-controls { margin-top: 10px; }\n");
        htmlContent.append("    .group-controls { margin-top: 10px; }\n");
        htmlContent.append("    .legend { position: absolute; bottom: 10px; right: 10px; background: white; padding: 10px; border-radius: 4px; border: 1px solid #ccc; }\n");
        htmlContent.append("    .legend-item { display: flex; align-items: center; margin: 5px 0; }\n");
        htmlContent.append("    .legend-color { width: 20px; height: 20px; margin-right: 5px; }\n");
        htmlContent.append("  </style>\n");
        htmlContent.append("</head>\n");
        htmlContent.append("<body>\n");
        htmlContent.append("  <div class=\"controls\">\n");
        htmlContent.append("    <input type=\"text\" class=\"search-box\" placeholder=\"Search methods...\">\n");
        htmlContent.append("    <select class=\"filter-box\">\n");
        htmlContent.append("      <option value=\"all\">All Methods</option>\n");
        htmlContent.append("      <option value=\"instance\">Instance Methods</option>\n");
        htmlContent.append("      <option value=\"static\">Static Methods</option>\n");
        htmlContent.append("      <option value=\"constructor\">Constructors</option>\n");
        htmlContent.append("      <option value=\"initializer\">Initializers</option>\n");
        htmlContent.append("    </select>\n");
        htmlContent.append("    <select class=\"filter-box\">\n");
        htmlContent.append("      <option value=\"all\">All Packages</option>\n");
        htmlContent.append("    </select>\n");
        htmlContent.append("    <select class=\"filter-box\">\n");
        htmlContent.append("      <option value=\"all\">All Complexity</option>\n");
        htmlContent.append("      <option value=\"low\">Low (1-5)</option>\n");
        htmlContent.append("      <option value=\"medium\">Medium (6-10)</option>\n");
        htmlContent.append("      <option value=\"high\">High (11-20)</option>\n");
        htmlContent.append("      <option value=\"very-high\">Very High (>20)</option>\n");
        htmlContent.append("    </select>\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <div class=\"path-controls\">\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"findShortestPath()\">Find Shortest Path</button>\n");
        htmlContent.append("    <input type=\"text\" class=\"search-box\" id=\"sourceNode\" placeholder=\"Source method...\">\n");
        htmlContent.append("    <input type=\"text\" class=\"search-box\" id=\"targetNode\" placeholder=\"Target method...\">\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <div class=\"group-controls\">\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"groupByPackage()\">Group by Package</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"groupByClass()\">Group by Class</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"resetGrouping()\">Reset Grouping</button>\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <div id=\"visualization\"></div>\n");
        htmlContent.append("  <div class=\"zoom-controls\">\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"zoomIn()\">+</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"zoomOut()\">-</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"resetZoom()\">Reset</button>\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <div class=\"legend\">\n");
        htmlContent.append("    <div class=\"legend-item\"><div class=\"legend-color instance\"></div>Instance Method</div>\n");
        htmlContent.append("    <div class=\"legend-item\"><div class=\"legend-color static\"></div>Static Method</div>\n");
        htmlContent.append("    <div class=\"legend-item\"><div class=\"legend-color constructor\"></div>Constructor</div>\n");
        htmlContent.append("    <div class=\"legend-item\"><div class=\"legend-color initializer\"></div>Initializer</div>\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <div class=\"export-controls\">\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"exportToPNG()\">Export PNG</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"exportToSVG()\">Export SVG</button>\n");
        htmlContent.append("    <button class=\"zoom-button\" onclick=\"exportToPDF()\">Export PDF</button>\n");
        htmlContent.append("  </div>\n");
        htmlContent.append("  <script>\n");

        // Prepare data for visualization
        Map<String, Set<String>> nodes = new HashMap<>();
        List<Map<String, Object>> links = new ArrayList<>();
        Map<String, Map<String, Object>> nodeData = new HashMap<>();

        // Collect nodes and links
        for (MethodCall call : calls) {
            String source = call.getCallerClass() + "." + call.getCallerMethod() + call.getCallerParameters();
            String target = call.getCalledClass() + "." + call.getCalledMethod() + call.getCalledParameters();
            
            // Add nodes
            if (!nodes.containsKey(source)) {
                nodes.put(source, new TreeSet<>());
                nodeData.put(source, createNodeData(call, true));
            }
            if (!nodes.containsKey(target)) {
                nodes.put(target, new TreeSet<>());
                nodeData.put(target, createNodeData(call, false));
            }
            
            // Add link
            Map<String, Object> link = new HashMap<>();
            link.put("source", source);
            link.put("target", target);
            link.put("lineNumber", call.getLineNumber());
            link.put("scope", call.getScope());
            link.put("context", getCallContext(call));
            links.add(link);
        }

        // Convert data to JSON
        htmlContent.append("    const data = {\n");
        htmlContent.append("      nodes: [\n");
        for (String node : nodes.keySet()) {
            Map<String, Object> data = nodeData.get(node);
            htmlContent.append(String.format("        { id: \"%s\", group: %d, methods: %d, complexity: %d },\n",
                escapeJavaScript(node),
                data.get("group"),
                data.get("methods"),
                data.get("complexity")));
        }
        htmlContent.append("      ],\n");
        htmlContent.append("      links: [\n");
        for (Map<String, Object> link : links) {
            htmlContent.append(String.format("        { source: \"%s\", target: \"%s\", lineNumber: %d, scope: \"%s\", context: \"%s\" },\n",
                escapeJavaScript((String) link.get("source")),
                escapeJavaScript((String) link.get("target")),
                link.get("lineNumber"),
                escapeJavaScript((String) link.get("scope")),
                escapeJavaScript((String) link.get("context"))));
        }
        htmlContent.append("      ]\n");
        htmlContent.append("    };\n\n");

        // Add D3.js visualization code with enhanced features
        htmlContent.append("    const width = 1200;\n");
        htmlContent.append("    const height = 800;\n");
        htmlContent.append("    let zoom = d3.zoom().scaleExtent([0.1, 4]);\n");
        htmlContent.append("    let currentGrouping = null;\n\n");
        htmlContent.append("    const svg = d3.select('#visualization')\n");
        htmlContent.append("      .append('svg')\n");
        htmlContent.append("      .attr('width', width)\n");
        htmlContent.append("      .attr('height', height)\n");
        htmlContent.append("      .call(zoom.on('zoom', (event) => {\n");
        htmlContent.append("        g.attr('transform', event.transform);\n");
        htmlContent.append("      }));\n\n");
        htmlContent.append("    const g = svg.append('g');\n\n");
        htmlContent.append("    const simulation = d3.forceSimulation(data.nodes)\n");
        htmlContent.append("      .force('link', d3.forceLink(data.links).id(d => d.id))\n");
        htmlContent.append("      .force('charge', d3.forceManyBody().strength(-300))\n");
        htmlContent.append("      .force('center', d3.forceCenter(width / 2, height / 2));\n\n");
        htmlContent.append("    const link = g.append('g')\n");
        htmlContent.append("      .selectAll('line')\n");
        htmlContent.append("      .data(data.links)\n");
        htmlContent.append("      .enter().append('line')\n");
        htmlContent.append("      .attr('class', 'link');\n\n");
        htmlContent.append("    const node = g.append('g')\n");
        htmlContent.append("      .selectAll('circle')\n");
        htmlContent.append("      .data(data.nodes)\n");
        htmlContent.append("      .enter().append('circle')\n");
        htmlContent.append("      .attr('class', d => `node ${d.type}`)\n");
        htmlContent.append("      .attr('r', d => Math.sqrt(d.methods) * 5 + 5);\n\n");
        htmlContent.append("    node.call(d3.drag()\n");
        htmlContent.append("      .on('start', dragstarted)\n");
        htmlContent.append("      .on('drag', dragged)\n");
        htmlContent.append("      .on('end', dragended));\n\n");
        htmlContent.append("    const tooltip = d3.select('body').append('div')\n");
        htmlContent.append("      .attr('class', 'tooltip')\n");
        htmlContent.append("      .style('opacity', 0);\n\n");
        htmlContent.append("    node.append('title')\n");
        htmlContent.append("      .text(d => d.id);\n\n");
        htmlContent.append("    node.on('mouseover', function(event, d) {\n");
        htmlContent.append("      tooltip.transition().duration(200).style('opacity', .9);\n");
        htmlContent.append("      tooltip.html('Method: ' + d.id + '<br/>' +\n");
        htmlContent.append("        'Type: ' + d.type + '<br/>' +\n");
        htmlContent.append("        'Methods: ' + d.methods + '<br/>' +\n");
        htmlContent.append("        'Complexity: ' + d.complexity + '<br/>' +\n");
        htmlContent.append("        'Package: ' + d.package)\n");
        htmlContent.append("        .style('left', (event.pageX + 10) + 'px')\n");
        htmlContent.append("        .style('top', (event.pageY - 28) + 'px');\n");
        htmlContent.append("    })\n");
        htmlContent.append("    .on('mouseout', function() {\n");
        htmlContent.append("      tooltip.transition().duration(500).style('opacity', 0);\n");
        htmlContent.append("    });\n\n");
        htmlContent.append("    // Path finding\n");
        htmlContent.append("    function findShortestPath() {\n");
        htmlContent.append("      const source = document.getElementById('sourceNode').value;\n");
        htmlContent.append("      const target = document.getElementById('targetNode').value;\n");
        htmlContent.append("      if (!source || !target) return;\n\n");
        htmlContent.append("      // Reset previous highlights\n");
        htmlContent.append("      link.classed('highlighted', false);\n\n");
        htmlContent.append("      // Find path using BFS\n");
        htmlContent.append("      const path = findPath(source, target);\n");
        htmlContent.append("      if (path) {\n");
        htmlContent.append("        path.forEach(edge => {\n");
        htmlContent.append("          link.filter(d => d.source.id === edge.source && d.target.id === edge.target)\n");
        htmlContent.append("            .classed('highlighted', true);\n");
        htmlContent.append("        });\n");
        htmlContent.append("      }\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function findPath(source, target) {\n");
        htmlContent.append("      const queue = [[source]];\n");
        htmlContent.append("      const visited = new Set([source]);\n\n");
        htmlContent.append("      while (queue.length > 0) {\n");
        htmlContent.append("        const path = queue.shift();\n");
        htmlContent.append("        const node = path[path.length - 1];\n\n");
        htmlContent.append("        if (node === target) {\n");
        htmlContent.append("          return path.slice(1).map((node, i) => ({\n");
        htmlContent.append("            source: path[i],\n");
        htmlContent.append("            target: node\n");
        htmlContent.append("          }));\n");
        htmlContent.append("        }\n\n");
        htmlContent.append("        data.links.forEach(link => {\n");
        htmlContent.append("          if (link.source.id === node && !visited.has(link.target.id)) {\n");
        htmlContent.append("            visited.add(link.target.id);\n");
        htmlContent.append("            queue.push([...path, link.target.id]);\n");
        htmlContent.append("          }\n");
        htmlContent.append("        });\n");
        htmlContent.append("      }\n");
        htmlContent.append("      return null;\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    // Grouping functions\n");
        htmlContent.append("    function groupByPackage() {\n");
        htmlContent.append("      currentGrouping = 'package';\n");
        htmlContent.append("      const packageGroups = d3.group(data.nodes, d => d.package);\n");
        htmlContent.append("      updateGrouping(packageGroups);\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function groupByClass() {\n");
        htmlContent.append("      currentGrouping = 'class';\n");
        htmlContent.append("      const classGroups = d3.group(data.nodes, d => d.class);\n");
        htmlContent.append("      updateGrouping(classGroups);\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function resetGrouping() {\n");
        htmlContent.append("      currentGrouping = null;\n");
        htmlContent.append("      simulation.force('group', null);\n");
        htmlContent.append("      simulation.alpha(1).restart();\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function updateGrouping(groups) {\n");
        htmlContent.append("      const groupForce = d3.forceCollide().radius(100);\n");
        htmlContent.append("      simulation.force('group', groupForce);\n");
        htmlContent.append("      simulation.alpha(1).restart();\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    // Enhanced filtering\n");
        htmlContent.append("    d3.selectAll('.filter-box').on('change', function() {\n");
        htmlContent.append("      const filterType = this.className;\n");
        htmlContent.append("      const filterValue = this.value;\n");
        htmlContent.append("      updateFilters();\n");
        htmlContent.append("    });\n\n");
        htmlContent.append("    function updateFilters() {\n");
        htmlContent.append("      const typeFilter = d3.select('.filter-box:nth-of-type(1)').node().value;\n");
        htmlContent.append("      const packageFilter = d3.select('.filter-box:nth-of-type(2)').node().value;\n");
        htmlContent.append("      const complexityFilter = d3.select('.filter-box:nth-of-type(3)').node().value;\n");
        htmlContent.append("      const searchText = d3.select('.search-box').node().value.toLowerCase();\n\n");
        htmlContent.append("      node.style('opacity', d => {\n");
        htmlContent.append("        if (searchText && !d.id.toLowerCase().includes(searchText)) return 0.1;\n");
        htmlContent.append("        if (typeFilter !== 'all' && d.type !== typeFilter) return 0.1;\n");
        htmlContent.append("        if (packageFilter !== 'all' && d.package !== packageFilter) return 0.1;\n");
        htmlContent.append("        if (complexityFilter !== 'all') {\n");
        htmlContent.append("          const complexity = d.complexity;\n");
        htmlContent.append("          if (complexityFilter === 'low' && complexity > 5) return 0.1;\n");
        htmlContent.append("          if (complexityFilter === 'medium' && (complexity <= 5 || complexity > 10)) return 0.1;\n");
        htmlContent.append("          if (complexityFilter === 'high' && (complexity <= 10 || complexity > 20)) return 0.1;\n");
        htmlContent.append("          if (complexityFilter === 'very-high' && complexity <= 20) return 0.1;\n");
        htmlContent.append("        }\n");
        htmlContent.append("        return 1;\n");
        htmlContent.append("      });\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    // Export functions\n");
        htmlContent.append("    function exportToPNG() {\n");
        htmlContent.append("      const svgData = new XMLSerializer().serializeToString(svg.node());\n");
        htmlContent.append("      const canvas = document.createElement('canvas');\n");
        htmlContent.append("      const ctx = canvas.getContext('2d');\n");
        htmlContent.append("      const img = new Image();\n");
        htmlContent.append("      img.onload = function() {\n");
        htmlContent.append("        canvas.width = width;\n");
        htmlContent.append("        canvas.height = height;\n");
        htmlContent.append("        ctx.drawImage(img, 0, 0);\n");
        htmlContent.append("        const link = document.createElement('a');\n");
        htmlContent.append("        link.download = 'method-calls.png';\n");
        htmlContent.append("        link.href = canvas.toDataURL('image/png');\n");
        htmlContent.append("        link.click();\n");
        htmlContent.append("      };\n");
        htmlContent.append("      img.src = 'data:image/svg+xml;base64,' + btoa(unescape(encodeURIComponent(svgData)));\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function exportToSVG() {\n");
        htmlContent.append("      const svgData = new XMLSerializer().serializeToString(svg.node());\n");
        htmlContent.append("      const blob = new Blob([svgData], {type: 'image/svg+xml'});\n");
        htmlContent.append("      const link = document.createElement('a');\n");
        htmlContent.append("      link.download = 'method-calls.svg';\n");
        htmlContent.append("      link.href = URL.createObjectURL(blob);\n");
        htmlContent.append("      link.click();\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("    function exportToPDF() {\n");
        htmlContent.append("      const svgData = new XMLSerializer().serializeToString(svg.node());\n");
        htmlContent.append("      // Note: PDF export requires server-side processing\n");
        htmlContent.append("      alert('PDF export requires server-side processing. Please use the Java export method.');\n");
        htmlContent.append("    }\n\n");
        htmlContent.append("  </script>\n");
        htmlContent.append("</body>\n");
        htmlContent.append("</html>");

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            writer.write(htmlContent.toString());
        }
    }

    private Map<String, Object> createNodeData(MethodCall call, boolean isCaller) {
        Map<String, Object> data = new HashMap<>();
        String className = isCaller ? call.getCallerClass() : call.getCalledClass();
        data.put("group", className.hashCode() % 10);
        data.put("methods", 1);
        data.put("complexity", calculateComplexity(call));
        return data;
    }

    private int calculateComplexity(MethodCall call) {
        int complexity = 1; // Base complexity
        if (call.isInTryBlock() || call.isInCatchBlock() || call.isInFinallyBlock()) complexity += 2;
        if (call.isInLoop()) complexity += 2;
        if (call.isInConditional()) complexity += 1;
        if (call.isOverloaded()) complexity += 1;
        if (call.isInherited()) complexity += 1;
        if (call.isPolymorphic()) complexity += 2;
        return complexity;
    }

    private String escapeJavaScript(String input) {
        return input.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }

    private String escapeDot(String input) {
        return input.replace("\"", "\\\"");
    }

    public void exportVisualization(String inputPath, String outputPath, String format) throws IOException {
        try {
            switch (format.toLowerCase()) {
                case "png":
                    exportToPNG(inputPath, outputPath);
                    break;
                case "svg":
                    exportToSVG(inputPath, outputPath);
                    break;
                case "pdf":
                    exportToPDF(inputPath, outputPath);
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported export format: " + format);
            }
            logger.info("Successfully exported visualization to {} format", format);
        } catch (Exception e) {
            logger.error("Failed to export visualization: {}", e.getMessage());
            throw new IOException("Failed to export visualization", e);
        }
    }

    private void exportToPNG(String inputPath, String outputPath) throws Exception {
        PNGTranscoder transcoder = new PNGTranscoder();
        transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, 1200f);
        transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, 800f);
        
        try (InputStream in = new FileInputStream(inputPath);
             OutputStream out = new FileOutputStream(outputPath)) {
            TranscoderInput input = new TranscoderInput(in);
            TranscoderOutput output = new TranscoderOutput(out);
            transcoder.transcode(input, output);
        }
    }

    private void exportToSVG(String inputPath, String outputPath) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputPath));
             BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private void exportToPDF(String inputPath, String outputPath) throws Exception {
        PDFTranscoder transcoder = new PDFTranscoder();
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_WIDTH, 1200f);
        transcoder.addTranscodingHint(SVGAbstractTranscoder.KEY_HEIGHT, 800f);
        
        try (InputStream in = new FileInputStream(inputPath);
             OutputStream out = new FileOutputStream(outputPath)) {
            TranscoderInput input = new TranscoderInput(in);
            TranscoderOutput output = new TranscoderOutput(out);
            transcoder.transcode(input, output);
        }
    }
} 