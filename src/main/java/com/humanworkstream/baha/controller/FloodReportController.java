package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.CreateReportRequest;
import com.humanworkstream.baha.dto.UpdateReportStatusRequest;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.service.FloodReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Flood reports. Reading is public; creating/confirming needs a login; resolving/deleting is
 * ADMIN-only (enforced in SecurityConfig).
 */
@RestController
@RequestMapping("/reports")
@RequiredArgsConstructor
public class FloodReportController {

    private final FloodReportService service;

    @GetMapping
    public List<FloodReport> list(@RequestParam(required = false) Severity severity) {
        return service.findActive(severity);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FloodReport> get(@PathVariable Integer id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FloodReport> create(@Valid @RequestBody CreateReportRequest req, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, userId));
    }

    @PostMapping("/{id}/confirm")
    public ResponseEntity<FloodReport> confirm(@PathVariable Integer id, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        return service.confirm(id, userId).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FloodReport> updateStatus(@PathVariable Integer id,
                                                    @Valid @RequestBody UpdateReportStatusRequest req) {
        return service.updateStatus(id, req.status())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
