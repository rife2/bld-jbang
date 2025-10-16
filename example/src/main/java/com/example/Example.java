package com.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Example {
    public static void main(String[] args) throws IOException {
        System.out.println(new Example().getMessage());
    }

    public String getMessage() throws IOException {
        return Files.readString(Paths.get("hello.java"));
    }
}