package com.humanworkstream.baha.service.social;

import com.humanworkstream.baha.config.SocialProperties;
import com.humanworkstream.baha.entity.FloodReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

/**
 * Publishes to the FloodedArea Facebook Page via the Graph API. With a photo: {@code POST
 * /{page-id}/photos} (the photo URL is this backend's own public photo endpoint — the same one
 * {@link com.humanworkstream.baha.service.ShareHtmlService} hands crawlers as {@code og:image}).
 * Without one: {@code POST /{page-id}/feed} with a {@code link} back to the report's own
 * {@code /reports/{id}/share} OG page, so the resulting link-card carries the report's details
 * instead of the generic site.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookPoster {

    private final SocialProperties props;
    private final RestClient http = SocialHttp.client();

    /**
     * @param apiOrigin this backend's own public origin (no trailing slash) — used to build the
     *                   photo URL and the /share link Facebook fetches.
     */
    public PostResult post(FloodReport report, String caption, String apiOrigin) {
        SocialProperties.Fb fb = props.getFb();
        if (fb.getPageId().isBlank() || fb.getPageToken().isBlank()) {
            return PostResult.permanentFailure("Facebook Page id/token not configured");
        }
        String base = "https://graph.facebook.com/" + fb.getGraphVersion() + "/" + fb.getPageId();
        try {
            Map<String, Object> body;
            if (report.getPhotoUrl() != null) {
                body = http.post()
                        .uri(base + "/photos")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form(
                                "url", apiOrigin + report.getPhotoUrl(),
                                "message", caption,
                                "access_token", fb.getPageToken()))
                        .retrieve()
                        .body(Map.class);
            } else {
                body = http.post()
                        .uri(base + "/feed")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(form(
                                "message", caption,
                                "link", apiOrigin + "/reports/" + report.getId() + "/share",
                                "access_token", fb.getPageToken()))
                        .retrieve()
                        .body(Map.class);
            }
            if (body == null) return PostResult.retryableFailure("Empty response from Graph API");
            Object postId = body.getOrDefault("post_id", body.get("id"));
            if (postId == null) return PostResult.retryableFailure("Graph API response had no post id: " + body);
            String id = String.valueOf(postId);
            return PostResult.ok(id, "https://www.facebook.com/" + id);
        } catch (RestClientResponseException e) {
            String errBody = e.getResponseBodyAsString();
            int status = e.getStatusCode().value();
            log.warn("[social] facebook post failed reportId={} status={} body={}", report.getId(), status, errBody);
            if (status == 400 || status == 401 || status == 403) {
                return PostResult.permanentFailure("HTTP " + status + ": " + truncate(errBody));
            }
            return PostResult.retryableFailure("HTTP " + status + ": " + truncate(errBody));
        } catch (Exception e) {
            log.warn("[social] facebook post errored reportId={}: {}", report.getId(), e.getMessage());
            return PostResult.retryableFailure(e.getMessage());
        }
    }

    private static MultiValueMap<String, String> form(String... kv) {
        MultiValueMap<String, String> m = new LinkedMultiValueMap<>();
        for (int i = 0; i < kv.length; i += 2) m.add(kv[i], kv[i + 1]);
        return m;
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 500 ? s.substring(0, 500) : s;
    }
}
