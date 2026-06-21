package com.humanworkstream.baha.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Liveness probe consumed by the UI's health gate before showing the workspace. */
@RestController
public class HealthCheckController {

    @GetMapping("/healthcheck")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
