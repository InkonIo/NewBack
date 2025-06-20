package com.example.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class TestController {
    
    @GetMapping("/hello")
    public String hello() {
        return "Hello from backend!";
    }

    @PostMapping("/echo")
    public String echo(@RequestBody String message) {
        return "You said: " + message;
    }
}
