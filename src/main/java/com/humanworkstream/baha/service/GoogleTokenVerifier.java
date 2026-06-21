package com.humanworkstream.baha.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.Optional;

/**
 * Server-side verification of Google Sign-In ID tokens. Delegates signature/expiry checking to
 * Google's tokeninfo endpoint, then asserts the token was minted for THIS app (the {@code aud}
 * claim must equal our configured client id) and that the email is verified. The client-supplied
 * email is never trusted — only the address Google returns for the verified token is used.
 */
@Slf4j
@Service
public class GoogleTokenVerifier {

    private static final String TOKENINFO = "https://oauth2.googleapis.com/tokeninfo?id_token={token}";

    private final String clientId;
    private final RestClient http = RestClient.create();

    public GoogleTokenVerifier(@Value("${google.client-id:}") String clientId) {
        this.clientId = clientId;
    }

    /** The verified Google identity. */
    public record GoogleProfile(String email, String name, String picture) {}

    /** True when a Google client id is configured (Google login is enabled). */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank();
    }

    /** Verifies the ID token and returns the proven identity, or empty if invalid/not configured. */
    public Optional<GoogleProfile> verify(String idToken) {
        if (!isConfigured()) {
            log.warn("[google] verification skipped — google.client-id is not configured");
            return Optional.empty();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> resp = http.get()
                    .uri(TOKENINFO, idToken)
                    .retrieve()
                    .body(Map.class);
            if (resp == null) return Optional.empty();

            String aud = str(resp.get("aud"));
            if (!clientId.equals(aud)) {
                log.warn("[google] token aud mismatch (expected this app's client id)");
                return Optional.empty();
            }
            boolean emailVerified = "true".equalsIgnoreCase(str(resp.get("email_verified")));
            String email = str(resp.get("email"));
            if (!emailVerified || email == null || email.isBlank()) {
                return Optional.empty();
            }
            String name = str(resp.get("name"));
            return Optional.of(new GoogleProfile(
                    email,
                    name != null ? name : email,
                    str(resp.get("picture"))));
        } catch (Exception e) {
            log.warn("[google] token verification failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
