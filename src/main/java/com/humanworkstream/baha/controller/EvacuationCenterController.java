package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.entity.EvacuationCenter;
import com.humanworkstream.baha.service.EvacuationCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Evacuation centers. Reading is public; create/patch/delete are ADMIN-only (SecurityConfig). */
@RestController
@RequestMapping("/centers")
@RequiredArgsConstructor
public class EvacuationCenterController {

    private final EvacuationCenterService service;

    @GetMapping
    public List<EvacuationCenter> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<EvacuationCenter> get(@PathVariable Integer id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<EvacuationCenter> create(@RequestBody EvacuationCenter center) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(center));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<EvacuationCenter> patch(@PathVariable Integer id, @RequestBody EvacuationCenter patch) {
        return service.patch(id, patch).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
