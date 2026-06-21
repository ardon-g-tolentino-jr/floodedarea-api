package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.AppUser;
import com.humanworkstream.baha.enumeration.AuthProvider;
import com.humanworkstream.baha.enumeration.UserRole;
import com.humanworkstream.baha.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Account lookup, self-service registration, Google account provisioning, and password checks. */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final AppUserRepository repository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Optional<AppUser> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email == null ? "" : email.trim());
    }

    @Transactional(readOnly = true)
    public Optional<AppUser> findById(Integer id) {
        return repository.findById(id);
    }

    /** Create a new CITIZEN with a BCrypt-hashed password. 409 if the email already exists. */
    @Transactional
    public AppUser register(String email, String rawPassword, String name) {
        String normalized = email.trim().toLowerCase();
        if (repository.existsByEmailIgnoreCase(normalized)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists.");
        }
        AppUser user = new AppUser();
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setName(name.trim());
        user.setRole(UserRole.CITIZEN);
        user.setProvider(AuthProvider.LOCAL);
        user.setIsNew(false);
        AppUser saved = repository.save(user);
        log.info("[users] registered citizen userId={} ({})", saved.getId(), normalized);
        return saved;
    }

    /** Find the user for a verified Google identity, creating a Google-only account on first sign-in. */
    @Transactional
    public AppUser findOrCreateGoogleUser(String email, String name, String picture) {
        String normalized = email.trim().toLowerCase();
        return repository.findByEmailIgnoreCase(normalized)
                .map(existing -> {
                    boolean changed = false;
                    if (picture != null && !picture.equals(existing.getPicture())) {
                        existing.setPicture(picture);
                        changed = true;
                    }
                    // Adopt Google as the provider only for accounts that never set a local password.
                    if (existing.getProvider() != AuthProvider.GOOGLE && existing.getPasswordHash() == null) {
                        existing.setProvider(AuthProvider.GOOGLE);
                        changed = true;
                    }
                    return changed ? repository.save(existing) : existing;
                })
                .orElseGet(() -> {
                    AppUser user = new AppUser();
                    user.setEmail(normalized);
                    user.setName(name != null && !name.isBlank() ? name : normalized);
                    user.setPicture(picture);
                    user.setRole(UserRole.CITIZEN);
                    user.setProvider(AuthProvider.GOOGLE);
                    user.setIsNew(false);
                    AppUser saved = repository.save(user);
                    log.info("[users] provisioned google citizen userId={} ({})", saved.getId(), normalized);
                    return saved;
                });
    }

    /** Verify a raw password against the stored BCrypt hash (false for Google-only accounts). */
    public boolean checkPassword(AppUser user, String rawPassword) {
        return user.getPasswordHash() != null
                && passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
