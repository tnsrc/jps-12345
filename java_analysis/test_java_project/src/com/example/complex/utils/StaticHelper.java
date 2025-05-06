package com.example.complex.utils;

import java.util.List;
import java.util.Map;

public class StaticHelper {

    public static final String STATIC_CONSTANT = "HelperConstant";
    private static int staticCounter = 0;

    public static void staticLog(String message) {
        // Simulate logging using System.out for simplicity
        System.out.println("[StaticHelper LOG] " + message);
        staticCounter++;
    }

    public static int getStaticCount() {
        return staticCounter;
    }

    public static String processList(List<String> items) {
        return "Processed " + items.size() + " items statically.";
    }

    // Overloaded static method
    public static String processList(Map<String, Integer> itemMap) {
        return "Processed map with " + itemMap.size() + " entries statically.";
    }
} 