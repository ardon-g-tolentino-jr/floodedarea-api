package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.CreateReportRequest;
import com.humanworkstream.baha.dto.UpdateReportStatusRequest;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.security.UserPrincipal;
import com.humanworkstream.baha.service.FloodReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Flood reports. Reading is public; creating/confirming needs a login; resolving (PATCH) is
 * ADMIN-only; deleting needs a login and is owner-or-admin (enforced in the service).
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

    /** Reports created in [from, to) regardless of status — powers the water-level history chart.
     *  The client passes the selected local day's bounds as ISO instants. */
    @GetMapping("/history")
    public List<FloodReport> history(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to) {
        return service.history(from, to);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FloodReport> get(@PathVariable Integer id) {
        return service.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Public: stream a report's photo. 404 when the report has no photo. */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Integer id) {
        return service.photo(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.mime()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .body(p.bytes()))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<FloodReport> create(@Valid @RequestBody CreateReportRequest req,
                                              Authentication auth, HttpServletRequest request) {
        UserPrincipal p = SecurityUtils.requirePrincipal(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, p.userId(), p.role(), clientIp(request)));
    }

    /** Real client IP behind a reverse proxy (X-Forwarded-For), falling back to the socket. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
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
    public ResponseEntity<Void> delete(@PathVariable Integer id, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        boolean admin = SecurityUtils.isAdmin(auth);
        return service.delete(id, userId, admin) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
