package com.joinlivora.backend.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLandingController {

    @GetMapping
    public Map<String, String> getLanding() {
        return Map.of("message", "Admin dashboard coming soon");
    }

    @GetMapping("/overview")
    public Map<String, String> getOverview() {
        return Map.of("message", "Admin panel – under construction");
    }
}
