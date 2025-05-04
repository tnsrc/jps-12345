package com.example.complex;

// Explicit imports
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.lang.System; // Explicit import of implicitly available class

// Wildcard imports
import java.io.*;
import java.net.*; // Another wildcard import

// Specific class imports
import com.example.complex.utils.*;
import com.example.complex.*;


public class TestComplex {

    // --- Fields ---
    private final String instanceId = "TestComplexInstance";
    private int counter = 0;
    private AnotherClass helper;
    private List<String> dataList;
    public static final String GREETING = "Hello"; // Constant String
    public static final int MAX_COUNT = 100;     // Constant number

    // --- Constructor ---
    public TestComplex() {
        this.helper = new AnotherClass("HelperForTestComplex");
        this.dataList = new ArrayList<>();
        dataList.add("Initial Data");
        System.out.println(instanceId + " initialized."); // Call to System.out
        StaticHelper.staticLog("TestComplex Constructor finished."); // Added call
    }

    // --- Methods ---

    // Basic method
    public void simpleMethod() {
        StaticHelper.staticLog("Entering simpleMethod"); // Static method call
        counter++;
        System.out.println("Counter incremented to: " + counter);
        this.complexMethod("data from simple", 5); // Call to another method in the same class
        this.helper.getName(); // Added call
        StaticHelper.staticLog("Exiting simpleMethod");
    }

    // Method with parameters and return type
    private String complexMethod(String input, int value) {
        String result = input + " processed with value " + value;
        dataList.add(result); // Method call on a field (dataList)
        StaticHelper.staticLog("complexMethod executed");
        StaticHelper.getStaticCount(); // Added call
        this.helper.getName(); // Added call
        System.out.println("Inside complexMethod helper name: " + this.helper.getName()); // Added calls (println + getName)
        return GREETING + " " + result; // Use constant
    }

    // Method demonstrating local variables and calls on them
    public void processItems() {
        List<String> localList = new ArrayList<>(this.dataList); // Use field
        localList.add("Local Item");
        String processedStatic = StaticHelper.processList(localList); // Static call with local variable
        System.out.println("Static processing result: " + processedStatic);

        Map<String, Integer> localMap = new HashMap<>();
        localMap.put("one", 1);
        processedStatic = StaticHelper.processList(localMap); // Call overloaded static method
        System.out.println("Static map processing result: " + processedStatic);

        AnotherClass localHelper = new AnotherClass("LocalHelper"); // Local variable of custom type
        try {
            localHelper.performAction(localList); // Method call on local variable, throws checked exception
        } catch (IOException e) {
            System.err.println("Caught expected exception: " + e.getMessage());
            // Using wildcard import File
            File errorFile = new File("error.log");
            System.out.println("Error log path: " + errorFile.getAbsolutePath());
        }
    }

    // Overloaded method 1
    public void handleData(String data) {
        System.out.println("Handling String data: " + data);
        this.helper.getName(); // Call on field 'helper'
        StaticHelper.staticLog("handleData(String) called"); // Added call
        this.dataList.size(); // Added call
        this.helper.createException("test in handleData"); // Added call
    }

    // Overloaded method 2 (different parameter type)
    public void handleData(int data) {
        System.out.println("Handling int data: " + (data + MAX_COUNT)); // Use constant
        StaticHelper.staticLog("handleData(int) called"); // Added call
        this.helper.getName(); // Added call
        this.dataList.isEmpty(); // Added call
        StaticHelper.getStaticCount(); // Added call
    }

    // Overloaded method 3 (different parameter count)
    public void handleData(String data, boolean flag) {
        System.out.println("Handling String data: " + data + " with flag: " + flag);
        StaticHelper.staticLog("handleData(String, boolean) called"); // Added call
        this.helper.getName(); // Added call
        this.dataList.add("handleData flag entry"); // Added call
        if (flag) {
            simpleMethod(); // Internal call (counts as 1)
        }
        StaticHelper.getStaticCount(); // Added call (Ensures 5 even if flag is false)
    }

    // Method throwing an exception
    public void riskyOperation(boolean shouldFail) throws IllegalArgumentException, ConnectException {
        StaticHelper.staticLog("Entering riskyOperation");
        if (shouldFail) {
            // Using wildcard import ConnectException
            throw new ConnectException("Network failure simulation");
        }
        // Method call returning an exception type
        Exception generatedEx = helper.createException("Generated during risky op");
        System.out.println("Generated exception type: " + generatedEx.getClass().getName());
        StaticHelper.staticLog("Exiting riskyOperation successfully");
    }

    // Method demonstrating inner class usage
    public void useInnerClass() {
        InnerWorker worker = new InnerWorker("WorkerBee");
        worker.doWork();
        String status = worker.getStatus(); // Call method on inner class instance
        System.out.println("Inner worker status: " + status);
        StaticHelper.staticLog("Finished using inner class"); // Added call
    }

    // Method with complex data type parameter (using generics)
    public Map<String, List<Integer>> processComplexData(Map<Integer, List<String>> inputMap) {
        System.out.println("Processing complex map data.");
        Map<String, List<Integer>> resultMap = new HashMap<>();
        for (Map.Entry<Integer, List<String>> entry : inputMap.entrySet()) {
            if (entry.getValue() != null) {
                 resultMap.put(entry.getValue().get(0), List.of(entry.getKey()));
            }
        }
        StaticHelper.staticLog("Complex data processed."); // Static call
        return resultMap;
    }


    // --- Inner Class ---
    private class InnerWorker {
        private String workerId;
        private int workCount = 0;

        public InnerWorker(String id) {
            this.workerId = id;
            // Accessing outer class field
            System.out.println("InnerWorker " + workerId + " created for " + instanceId);
            StaticHelper.staticLog("InnerWorker instance created: " + id); // Added call
            TestComplex.this.helper.getName(); // Added call
            this.getStatus(); // Added call
            System.out.println("InnerWorker constructor finished."); // Added call
        }

        public void doWork() {
            workCount++;
            System.out.println(workerId + " working... Count: " + workCount);
            // Call outer class method
            TestComplex.this.simpleMethod();
            // Call static method from outer scope
            StaticHelper.staticLog("InnerWorker " + workerId + " finished work cycle.");
            this.getStatus(); // Added call
            System.out.println("Outer instance ID: " + TestComplex.this.instanceId); // Added call
        }

        public String getStatus() {
             // Access outer constant
             StaticHelper.staticLog("Getting InnerWorker status for " + workerId); // Added call
             TestComplex.this.helper.getName(); // Added call
             TestComplex.this.dataList.size(); // Added call
             System.out.println("Status check print for " + this.workerId); // Added call
             StaticHelper.getStaticCount(); // Added call
             return workerId + " status: OK. Max allowed: " + MAX_COUNT;
        }
    }


    // --- Main Method ---
    public static void main(String[] args) {
        System.out.println("--- TestComplex Starting ---"); // Direct System.out call
        StaticHelper.staticLog("Application Start"); // Static call

        TestComplex testInstance = new TestComplex(); // Constructor call

        // Demonstrate various method calls
        testInstance.simpleMethod();
        testInstance.processItems();
        testInstance.handleData("Some String");
        testInstance.handleData(42);
        testInstance.handleData("Another String", true);

        try {
            testInstance.riskyOperation(false);
            testInstance.riskyOperation(true); // This should throw
        } catch (ConnectException e) {
            System.err.println("Caught ConnectException in main: " + e.getMessage());
        } catch (IllegalArgumentException e) {
             System.err.println("Caught IllegalArgumentException in main: " + e.getMessage());
        }

        testInstance.useInnerClass();

        Map<Integer, List<String>> complexInput = new HashMap<>();
        complexInput.put(1, List.of("One", "Uno"));
        complexInput.put(2, List.of("Two", "Dos"));
        Map<String, List<Integer>> complexResult = testInstance.processComplexData(complexInput);
        System.out.println("Complex result map size: " + complexResult.size());


        System.out.println("Final static count: " + StaticHelper.getStaticCount()); // Static method call
        System.out.println("--- TestComplex Finished ---");
    }
} 