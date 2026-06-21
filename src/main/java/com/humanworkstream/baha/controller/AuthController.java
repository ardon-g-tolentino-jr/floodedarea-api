package com.humanworkstream.baha.controller;

import com.humanworkstream.baha.dto.AuthResponse;
import com.humanworkstream.baha.dto.GoogleAuthRequest;
import com.humanworkstream.baha.dto.LoginRequest;
import com.humanworkstream.baha.dto.RegisterRequest;
import com.humanworkstream.baha.entity.AppUser;
import com.humanworkstream.baha.security.JwtUtil;
import com.humanworkstream.baha.security.SecurityUtils;
import com.humanworkstream.baha.security.UserPrincipal;
import com.humanworkstream.baha.service.GoogleTokenVerifier;
import com.humanworkstream.baha.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Self-contained authentication: Basic login, self-service registration, and Google SSO. */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final GoogleTokenVerifier googleVerifier;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AppUser user = userService.findByEmail(req.email())
                .filter(u -> userService.checkPassword(u, req.password()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password."));
        return ResponseEntity.ok(session(user));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        AppUser user = userService.register(req.email(), req.password(), req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(session(user));
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleAuthRequest req) {
        GoogleTokenVerifier.GoogleProfile profile = googleVerifier.verify(req.idToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Could not verify your Google sign-in. Please try again."));
        // Bind the session to the Google-verified email, never to anything in the request body.
        AppUser user = userService.findOrCreateGoogleUser(profile.email(), profile.name(), profile.picture());
        return ResponseEntity.ok(session(user));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(Authentication auth) {
        UserPrincipal principal = SecurityUtils.requirePrincipal(auth);
        AppUser user = userService.findById(principal.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
        return ResponseEntity.ok(new AuthResponse(null, user.getId(), user.getEmail(), user.getName(),
                user.getPicture(), user.getRole().name(), Boolean.TRUE.equals(user.getIsNew())));
    }

    private AuthResponse session(AppUser user) {
        String token = jwtUtil.generate(user.getEmail(), user.getId(), user.getRole().name());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getName(),
                user.getPicture(), user.getRole().name(), Boolean.TRUE.equals(user.getIsNew()));
    }
}
