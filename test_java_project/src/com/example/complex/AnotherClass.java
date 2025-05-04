package com.example.complex;

import java.util.List;
import java.io.IOException;

public class AnotherClass {

    private String instanceName;

    public AnotherClass(String name) {
        this.instanceName = name;
        System.out.println("AnotherClass instance '" + name + "' created.");
    }

    public String getName() {
        return this.instanceName;
    }

    public void performAction(List<String> data) throws IOException {
        if (data.isEmpty()) {
            throw new IOException("Cannot perform action on empty data in " + instanceName);
        }
        System.out.println(instanceName + " performing action on data: " + data.get(0));
        createException("test in performAction");
    }

    public Exception createException(String message) {
        System.out.println("Hello".toLowerCase());
        return new IllegalArgumentException(instanceName + ": " + message);
    }
} 