package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.entity.Hotline;
import com.humanworkstream.baha.service.HotlineService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Emergency hotlines. Reading is public; create/patch/delete are ADMIN-only (SecurityConfig). */
@RestController
@RequestMapping("/hotlines")
@RequiredArgsConstructor
public class HotlineController {

    private final HotlineService service;

    @GetMapping
    public List<Hotline> list() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<Hotline> create(@RequestBody Hotline hotline) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(hotline));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Hotline> patch(@PathVariable Integer id, @RequestBody Hotline patch) {
        return service.patch(id, patch).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
