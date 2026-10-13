package com.example.caching.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class GreetingController {

    @GetMapping("/greeting")
    public Map<String, Object> getGreeting() {
        return Map.of(
            "status", "SUCCESS",
            "message", "CI/CD Pipeline with Gradle Dependency Caching is working!",
            "session", "Session 11 - Exercise 3"
        );
    }
}
