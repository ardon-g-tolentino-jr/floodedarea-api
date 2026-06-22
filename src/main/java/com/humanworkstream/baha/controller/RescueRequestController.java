package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.CreateRescueRequest;
import com.humanworkstream.baha.dto.UpdateRescueStatusRequest;
import com.humanworkstream.baha.entity.RescueRequest;
import com.humanworkstream.baha.enumeration.RescueStatus;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.service.RescueRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SOS rescue requests. Any logged-in user can POST one and view the queue (which powers the
 * per-district chart); advancing/clearing a request's status (PATCH/DELETE) is ADMIN-only
 * (SecurityConfig).
 */
@RestController
@RequestMapping("/rescue")
@RequiredArgsConstructor
public class RescueRequestController {

    private final RescueRequestService service;

    @PostMapping
    public ResponseEntity<RescueRequest> create(@Valid @RequestBody CreateRescueRequest req, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, userId));
    }

    @GetMapping
    public List<RescueRequest> list(@RequestParam(required = false) RescueStatus status) {
        return service.findAll(status);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<RescueRequest> updateStatus(@PathVariable Integer id,
                                                      @Valid @RequestBody UpdateRescueStatusRequest req) {
        return service.updateStatus(id, req.status())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
