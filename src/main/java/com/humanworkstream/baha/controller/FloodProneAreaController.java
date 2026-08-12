package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.BulkAlertResult;
import com.humanworkstream.baha.dto.BulkWarnAreasRequest;
import com.humanworkstream.baha.dto.ClearAreaWarningsRequest;
import com.humanworkstream.baha.dto.WarnAreaRequest;
import com.humanworkstream.baha.entity.FloodAlert;
import com.humanworkstream.baha.entity.FloodProneArea;
import com.humanworkstream.baha.service.FloodProneAreaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-pinned flood-prone areas (pin + radius). {@code GET /flood-prone} returns the active
 * zones for the map (public); {@code GET /flood-prone/all} and writes are ADMIN-only
 * (SecurityConfig). {@code POST /flood-prone/{id}/warn} manually broadcasts a geo-stamped
 * {@link FloodAlert} for the zone.
 */
@RestController
@RequestMapping("/flood-prone")
@RequiredArgsConstructor
public class FloodProneAreaController {

    private final FloodProneAreaService service;

    @GetMapping
    public List<FloodProneArea> active() {
        return service.findActive();
    }

    @GetMapping("/all")
    public List<FloodProneArea> all() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<FloodProneArea> create(@RequestBody FloodProneArea area) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(area));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<FloodProneArea> patch(@PathVariable Integer id, @RequestBody FloodProneArea patch) {
        return service.patch(id, patch).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        return service.delete(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/warn")
    public ResponseEntity<FloodAlert> warn(@PathVariable Integer id, @RequestBody(required = false) WarnAreaRequest req) {
        WarnAreaRequest body = req != null ? req : new WarnAreaRequest(null, null, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.warn(id, body));
    }

    /**
     * Warn many areas at once — one transaction, one SSE broadcast. Distinct path segment from
     * {@code /{id}/warn} (one segment vs. two), so the two mappings can't collide.
     */
    @PostMapping("/warn-bulk")
    public ResponseEntity<BulkAlertResult> warnBulk(@RequestBody BulkWarnAreasRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.warnBulk(req));
    }

    /** Deactivate the standing warnings raised from the listed areas — the undo for warn-bulk. */
    @PostMapping("/clear-warnings")
    public ResponseEntity<BulkAlertResult> clearWarnings(@RequestBody ClearAreaWarningsRequest req) {
        return ResponseEntity.ok(service.clearWarnings(req));
    }
}
