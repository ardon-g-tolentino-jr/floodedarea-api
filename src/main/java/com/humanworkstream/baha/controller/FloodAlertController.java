package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.entity.FloodAlert;
import com.humanworkstream.baha.service.FloodAlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Flood / rainfall alerts. {@code GET /alerts} returns the active alerts for the ribbon (public);
 * {@code GET /alerts/all} and writes are ADMIN-only (SecurityConfig).
 */
@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
public class FloodAlertController {

    private final FloodAlertService service;

    @GetMapping
    public List<FloodAlert> active() {
        return service.findActive();
    }

    @GetMapping("/all")
    public List<FloodAlert> all() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<FloodAlert> create(@RequestBody FloodAlert alert) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(alert));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FloodAlert> patch(@PathVariable Integer id, @RequestBody FloodAlert patch) {
        return service.patch(id, patch).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
