package com.humanworkstream.baha.service.social;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Hand-rolled OAuth 1.0a HMAC-SHA1 request signing for X (Twitter) API v2 / v1.1 uploads. X's
 * user-context access tokens don't expire, so there's no refresh flow to operate — just a
 * signature per request. No external OAuth library; ~60 lines with {@link Mac} is simpler than a
 * new Maven dependency for one call site.
 */
@Component
public class OAuth1Signer {

    private static final String SIG_METHOD = "HMAC-SHA1";
    private static final String VERSION = "1.0";
    private final SecureRandom random = new SecureRandom();

    /**
     * Builds the {@code Authorization: OAuth ...} header value for a request.
     *
     * @param method     HTTP method, upper-case ("POST")
     * @param url        the base URL, no query string
     * @param params     query/body params to include in the signature base string (form-encoded
     *                   body params for a classic POST, or query params for a GET)
     * @param apiKey     consumer key
     * @param apiSecret  consumer secret
     * @param token      user access token
     * @param tokenSecret user access token secret
     */
    public String authorizationHeader(String method, String url, Map<String, String> params,
                                       String apiKey, String apiSecret, String token, String tokenSecret) {
        SortedMap<String, String> oauthParams = new TreeMap<>();
        oauthParams.put("oauth_consumer_key", apiKey);
        oauthParams.put("oauth_nonce", nonce());
        oauthParams.put("oauth_signature_method", SIG_METHOD);
        oauthParams.put("oauth_timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        oauthParams.put("oauth_token", token);
        oauthParams.put("oauth_version", VERSION);

        SortedMap<String, String> all = new TreeMap<>(oauthParams);
        if (params != null) all.putAll(params);

        String baseString = method.toUpperCase() + "&" + enc(url) + "&" + enc(paramString(all));
        String signingKey = enc(apiSecret) + "&" + enc(tokenSecret == null ? "" : tokenSecret);
        String signature = hmacSha1(baseString, signingKey);

        oauthParams.put("oauth_signature", signature);
        StringBuilder header = new StringBuilder("OAuth ");
        boolean first = true;
        for (Map.Entry<String, String> e : oauthParams.entrySet()) {
            if (!first) header.append(", ");
            header.append(enc(e.getKey())).append("=\"").append(enc(e.getValue())).append('"');
            first = false;
        }
        return header.toString();
    }

    private String paramString(SortedMap<String, String> params) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            if (!first) sb.append('&');
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
            first = false;
        }
        return sb.toString();
    }

    private String hmacSha1(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign OAuth1 request", e);
        }
    }

    private String nonce() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes).replaceAll("[^A-Za-z0-9]", "");
    }

    /** RFC 3986 percent-encoding (stricter than {@link URLEncoder}: space stays %20, not '+'). */
    private static String enc(String s) {
        if (s == null) return "";
        String encoded = URLEncoder.encode(s, StandardCharsets.UTF_8);
        return encoded.replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }
}
