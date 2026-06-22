package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.CreateFeedbackRequest;
import com.humanworkstream.baha.dto.UpdateFeedbackStatusRequest;
import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User feedback — bug reports & feature requests. Submitting and reading the accepted-features list
 * need a login (any registered user); the full queue and accept/decline are ADMIN-only (SecurityConfig).
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    /** Accepted feature requests — shown to every registered user. */
    @GetMapping("/accepted")
    public List<Feedback> accepted() {
        return service.acceptedFeatures();
    }

    /** Full moderation queue (ADMIN). */
    @GetMapping
    public List<Feedback> all() {
        return service.findAll();
    }

    @PostMapping
    public ResponseEntity<Feedback> create(@Valid @RequestBody CreateFeedbackRequest req, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(req, userId));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Feedback> updateStatus(@PathVariable Integer id,
                                                 @Valid @RequestBody UpdateFeedbackStatusRequest req) {
        return service.updateStatus(id, req.status())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
