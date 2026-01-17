package com.joinlivora.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model")
public class ModelController {

    @GetMapping("/stream")
    public String stream() {
        return "🎥 Model stream area";
    }
}
