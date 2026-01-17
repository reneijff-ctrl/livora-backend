package com.joinlivora.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ApiController {

    @GetMapping("/api/secure")
    public String secureEndpoint() {
        return "✅ Secure USER endpoint";
    }
}
