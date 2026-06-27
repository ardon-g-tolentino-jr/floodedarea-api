package com.humanworkstream.baha.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Server-side verification of Cloudflare Turnstile tokens for no-login guest writes. When no
 * secret is configured ({@code turnstile.secret-key} empty) verification is DISABLED and
 * {@link #verify} returns true — mirroring how Google SSO is optional. When enabled it fails
 * closed: a missing/invalid token or a verification error returns false.
 */
@Slf4j
@Service
public class TurnstileVerifier {

    private static final String SITEVERIFY = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final RestClient http = RestClient.create();

    public TurnstileVerifier(@Value("${turnstile.secret-key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    /** True when a Turnstile secret is configured (guest submissions must then carry a token). */
    public boolean isEnabled() {
        return secretKey != null && !secretKey.isBlank();
    }

    /** True when the token is valid, or when Turnstile is disabled. False only when enabled and the token fails. */
    public boolean verify(String token, String remoteIp) {
        if (!isEnabled()) return true;
        if (token == null || token.isBlank()) return false;
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("secret", secretKey);
            form.add("response", token);
            if (remoteIp != null && !remoteIp.isBlank()) form.add("remoteip", remoteIp);
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.post()
                    .uri(SITEVERIFY)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return resp != null && Boolean.TRUE.equals(resp.get("success"));
        } catch (Exception e) {
            log.warn("[turnstile] token verification failed: {}", e.getMessage());
            return false;
        }
    }
}
