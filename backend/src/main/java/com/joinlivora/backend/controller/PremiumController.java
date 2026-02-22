package com.joinlivora.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premium")
public class PremiumController {

    @GetMapping("/content")
    @PreAuthorize("hasAnyRole('PREMIUM', 'ADMIN')")
    public String content() {
        return "💎 Premium content";
    }
}
