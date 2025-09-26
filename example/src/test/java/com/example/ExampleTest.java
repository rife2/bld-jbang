package com.example;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class ExampleTest {
    @Test
    void verifyHelloWold() throws IOException {
        assertTrue(new Example().getMessage().contains("Hello World"));
    }
}
