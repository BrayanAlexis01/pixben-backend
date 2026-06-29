package com.pixben.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigController {

    @Value("${spring.data.mongodb.uri}")
    private String mongoUri;

    @GetMapping("/config")
    public String config() {
        return mongoUri;
    }
}