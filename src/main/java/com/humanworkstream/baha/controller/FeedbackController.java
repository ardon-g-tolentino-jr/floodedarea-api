package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.CreateFeedbackRequest;
import com.humanworkstream.baha.dto.UpdateFeedbackStatusRequest;
import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.security.UserPrincipal;
import com.humanworkstream.baha.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * User feedback — bug reports & feature requests. The accepted board is a public read; submitting and
 * upvoting need a session (GUESTs allowed, like report posting); the full queue and accept/decline are
 * ADMIN-only (see SecurityConfig).
 */
@RestController
@RequestMapping("/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService service;

    /** Public board: accepted bugs + features, ordered by upvotes. Personalized with the caller's votes
     *  when a session is present (anonymous still works — every item just reads upvotedByMe=false). */
    @GetMapping("/accepted")
    public List<Feedback> accepted(Authentication auth) {
        return service.acceptedFeedback(SecurityUtils.optionalUserId(auth));
    }

    /** Full moderation queue (ADMIN). */
    @GetMapping
    public List<Feedback> all(Authentication auth) {
        return service.findAll(SecurityUtils.optionalUserId(auth));
    }

    @PostMapping
    public ResponseEntity<Feedback> create(@Valid @RequestBody CreateFeedbackRequest req,
                                           Authentication auth, HttpServletRequest request) {
        UserPrincipal p = SecurityUtils.requirePrincipal(auth);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(req, p.userId(), p.role(), clientIp(request)));
    }

    /** Toggle the caller's upvote on an item (account or guest). */
    @PostMapping("/{id}/upvote")
    public ResponseEntity<Feedback> upvote(@PathVariable Integer id, Authentication auth) {
        int userId = SecurityUtils.requireUserId(auth);
        return service.toggleUpvote(id, userId)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Feedback> updateStatus(@PathVariable Integer id,
                                                 @Valid @RequestBody UpdateFeedbackStatusRequest req) {
        return service.updateStatus(id, req.status())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** Real client IP behind a reverse proxy (X-Forwarded-For), falling back to the socket. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
