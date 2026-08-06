package com.humanworkstream.baha.service.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.humanworkstream.baha.config.SocialProperties;
import com.humanworkstream.baha.entity.FloodReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Publishes a tweet via X API v2, signed with OAuth 1.0a (see {@link OAuth1Signer}) — user
 * access tokens on X don't expire, so there's no refresh plumbing. When the report has a photo it
 * is uploaded first via the v1.1 media endpoint and attached to the tweet; text-only otherwise.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XPoster {

    private static final String TWEETS_URL = "https://api.twitter.com/2/tweets";
    private static final String MEDIA_UPLOAD_URL = "https://upload.twitter.com/1.1/media/upload.json";
    private static final int MAX_TWEET_CHARS = 280;
    private static final int URL_WEIGHT = 23; // X counts every URL as 23 chars regardless of length

    private final SocialProperties props;
    private final OAuth1Signer signer;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http = SocialHttp.client();

    public PostResult post(FloodReport report, String captionWithLink, byte[] photoBytes, String photoMime) {
        SocialProperties.X x = props.getX();
        if (x.getApiKey().isBlank() || x.getApiSecret().isBlank()
                || x.getAccessToken().isBlank() || x.getAccessSecret().isBlank()) {
            return PostResult.permanentFailure("X API credentials not configured");
        }
        try {
            String text = budgetText(captionWithLink);
            String mediaId = null;
            if (photoBytes != null && photoBytes.length > 0) {
                try {
                    mediaId = uploadMedia(photoBytes, photoMime, x);
                } catch (Exception e) {
                    // A media upload failure shouldn't sink the whole tweet — post text-only.
                    log.warn("[social] x media upload failed reportId={}: {}", report.getId(), e.getMessage());
                }
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text);
            if (mediaId != null) {
                body.put("media", Map.of("media_ids", new String[]{mediaId}));
            }
            String auth = signer.authorizationHeader("POST", TWEETS_URL, Map.of(),
                    x.getApiKey(), x.getApiSecret(), x.getAccessToken(), x.getAccessSecret());
            Map<String, Object> resp = http.post()
                    .uri(TWEETS_URL)
                    .header(HttpHeaders.AUTHORIZATION, auth)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp == null || !(resp.get("data") instanceof Map<?, ?> data) || data.get("id") == null) {
                return PostResult.retryableFailure("Empty/unexpected response from X: " + resp);
            }
            String id = String.valueOf(data.get("id"));
            return PostResult.ok(id, "https://x.com/i/web/status/" + id);
        } catch (RestClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            int status = e.getStatusCode().value();
            log.warn("[social] x post failed reportId={} status={} body={}", report.getId(), status, errBody);
            if (status == 400 || status == 401 || status == 403) {
                return PostResult.permanentFailure("HTTP " + status + ": " + truncate(errBody));
            }
            return PostResult.retryableFailure("HTTP " + status + ": " + truncate(errBody));
        } catch (Exception e) {
            log.warn("[social] x post errored reportId={}: {}", report.getId(), e.getMessage());
            return PostResult.retryableFailure(e.getMessage());
        }
    }

    /** Uploads image bytes to the v1.1 media endpoint (multipart, OAuth1-signed) and returns media_id_string. */
    private String uploadMedia(byte[] bytes, String mime, SocialProperties.X x) throws Exception {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("media_data", Base64.getEncoder().encodeToString(bytes));
        if (mime != null) form.add("media_category", "tweet_image");
        // OAuth1 signature base string for a form-encoded body includes the body params.
        Map<String, String> sigParams = new TreeMap<>();
        sigParams.put("media_data", Base64.getEncoder().encodeToString(bytes));
        if (mime != null) sigParams.put("media_category", "tweet_image");
        String auth = signer.authorizationHeader("POST", MEDIA_UPLOAD_URL, sigParams,
                x.getApiKey(), x.getApiSecret(), x.getAccessToken(), x.getAccessSecret());
        String rawResp = http.post()
                .uri(MEDIA_UPLOAD_URL)
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(String.class);
        Map<?, ?> parsed = mapper.readValue(rawResp, Map.class);
        Object mediaId = parsed.get("media_id_string");
        if (mediaId == null) throw new IllegalStateException("media upload response had no media_id_string");
        return String.valueOf(mediaId);
    }

    /** Truncates to X's 280-char budget, reserving {@link #URL_WEIGHT} chars for a trailing URL
     *  (X counts any URL as exactly 23 chars regardless of its real length). Assumes the caption's
     *  final line is the link, added by the caller before this is invoked. */
    private static String budgetText(String captionWithLink) {
        if (captionWithLink.length() <= MAX_TWEET_CHARS) return captionWithLink;
        int lastNewline = captionWithLink.lastIndexOf('\n');
        String body = lastNewline >= 0 ? captionWithLink.substring(0, lastNewline) : captionWithLink;
        String link = lastNewline >= 0 ? captionWithLink.substring(lastNewline) : "";
        int budget = MAX_TWEET_CHARS - (link.isBlank() ? 0 : URL_WEIGHT + 1) - 1; // +1 for the newline, -1 for an ellipsis
        if (budget < 0) budget = 0;
        String truncatedBody = body.length() > budget ? body.substring(0, budget) + "…" : body;
        return link.isBlank() ? truncatedBody : truncatedBody + link;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
