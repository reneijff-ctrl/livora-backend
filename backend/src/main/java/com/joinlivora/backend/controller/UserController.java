package com.joinlivora.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @GetMapping("/me")
    public String userInfo() {
        return "👤 Ingelogde gebruiker";
    }

    @PostMapping("/profile")
    public String updateProfile() {
        return "✅ Profile updated";
    }

    @GetMapping("/csrf")
    public void getCsrf() {
        // Just to trigger CSRF cookie generation if needed
    }
}
