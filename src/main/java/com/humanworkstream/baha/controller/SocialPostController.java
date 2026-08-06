package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.entity.SocialPost;
import com.humanworkstream.baha.repository.SocialPostRepository;
import com.humanworkstream.baha.service.SocialPublishService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ADMIN-only visibility into Facebook/X auto-post attempts (see SecurityConfig — every route
 * here is registered ADMIN-only) and a manual retry for rows the scheduled sweep gave up on.
 */
@RestController
@RequestMapping("/social-posts")
@RequiredArgsConstructor
public class SocialPostController {

    private final SocialPostRepository repository;
    private final SocialPublishService publishService;

    /** Most recent 50 publish attempts across all reports, newest first. */
    @GetMapping
    public List<SocialPost> recent() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    /** Resets attempts and republishes one row (fire-and-forget — the row's status catches up
     *  moments later; the admin panel polls/refetches to see it). */
    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retry(@PathVariable Integer id) {
        if (repository.findById(id).isEmpty()) return ResponseEntity.notFound().build();
        publishService.retry(id);
        return ResponseEntity.accepted().build();
    }
}
