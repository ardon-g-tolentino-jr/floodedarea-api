package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.ApproveIncidentRequest;
import com.humanworkstream.baha.dto.UpdateIncidentStatusRequest;
import com.humanworkstream.baha.entity.DraftIncident;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.IncidentStatus;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.service.DraftIncidentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * News-drafted flood incidents — the admin review queue. Every route is ADMIN-only (see
 * SecurityConfig). Approving a draft promotes it to a system flood report; dismissing drops it.
 */
@RestController
@RequestMapping("/incidents")
@RequiredArgsConstructor
public class DraftIncidentController {

    private final DraftIncidentService service;

    /** The review queue, filtered by status (defaults to {@code pending} — what the inbox shows). */
    @GetMapping
    public List<DraftIncident> list(@RequestParam(required = false) IncidentStatus status) {
        return service.findByStatus(status != null ? status : IncidentStatus.pending);
    }

    /** Approve a draft (optionally with edits) → creates the system report and returns it. */
    @PostMapping("/{id}/approve")
    public ResponseEntity<FloodReport> approve(@PathVariable Integer id,
                                               @RequestBody(required = false) ApproveIncidentRequest req,
                                               Authentication auth) {
        int adminUserId = SecurityUtils.requireUserId(auth);
        return service.approve(id, req != null ? req : ApproveIncidentRequest.empty(), adminUserId)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Update a draft's review status (e.g. {@code dismissed}). */
    @PatchMapping("/{id}")
    public ResponseEntity<DraftIncident> updateStatus(@PathVariable Integer id,
                                                      @Valid @RequestBody UpdateIncidentStatusRequest req) {
        return service.updateStatus(id, req.status())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
