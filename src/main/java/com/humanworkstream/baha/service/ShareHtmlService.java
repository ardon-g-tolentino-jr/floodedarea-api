package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.FloodReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Renders the tiny HTML page social-media crawlers (Facebook, X, WhatsApp, …) read to build a link
 * preview. Crawlers don't run JavaScript, so the SPA's runtime state is invisible to them; nginx
 * routes a crawler requesting a shared report link ({@code /?report=<id>}) here (via
 * {@code GET /reports/{id}/share}) so the preview card shows the report's own details instead of the
 * generic site cover. Absolute URLs (required by crawlers) are built from {@code baha.public-base-url}.
 */
@Service
public class ShareHtmlService {

    private final String base; // public site origin, no trailing slash

    public ShareHtmlService(@Value("${baha.public-base-url}") String publicBaseUrl) {
        this.base = publicBaseUrl.endsWith("/")
                ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
    }

    /**
     * Per-report Open Graph / Twitter card: severity, depth, trend, location, and the photo when
     * present. `apiBase` is the public origin of THIS backend (derived from the incoming request) —
     * the report photo is streamed by this API, so its `og:image` must point there, not at the static
     * site (which doesn't proxy the API).
     */
    public String renderReport(FloodReport r, String apiBase) {
        String deepLink = base + "/?report=" + r.getId();
        String sev = r.getSeverity().name().toUpperCase(); // HIGH / MEDIUM / LOW
        String where = r.getDistrict() != null && !r.getDistrict().isBlank()
                ? r.getName() + " (" + r.getDistrict() + ")"
                : r.getName();
        String trend = switch (r.getTrend()) {
            case rising -> ", rising";
            case receding -> ", receding";
            case steady -> "";
        };
        String title = sev + " flooding — " + r.getName() + " · FloodedArea";
        String desc = "~" + r.getDepthCm() + " cm" + trend + " at " + where
                + ". Live crowd-sourced flood report on FloodedArea — real-time flood map for the Philippines.";
        // The photo is streamed by this backend at /reports/{id}/photo — use the API's own public
        // origin so the crawler can actually fetch it. Fall back to the site-wide cover (a static file
        // on the public site) when the report has no photo.
        String apiOrigin = apiBase.endsWith("/") ? apiBase.substring(0, apiBase.length() - 1) : apiBase;
        String image = r.getPhotoUrl() != null
                ? apiOrigin + r.getPhotoUrl()
                : base + "/og-cover.jpg?v=1";
        return html(title, desc, image, deepLink);
    }

    /** Site-wide card for a missing/invalid report id (mirrors index.html's static OG tags). */
    public String renderDefault() {
        return html(
                "FloodedArea — Live Flood Watch for the Philippines",
                "Crowd-sourced, real-time flood map for the Philippines: live reports, evacuation centers, "
                        + "road conditions, rainfall alerts and SOS rescue.",
                base + "/og-cover.jpg?v=1",
                base + "/");
    }

    private String html(String title, String desc, String image, String url) {
        String t = esc(title);
        String d = esc(desc);
        String img = esc(image);
        String u = esc(url);
        return """
                <!DOCTYPE html>
                <html lang="en"><head><meta charset="UTF-8"/>
                <title>%s</title>
                <meta name="description" content="%s"/>
                <meta property="og:type" content="website"/>
                <meta property="og:site_name" content="FloodedArea"/>
                <meta property="og:title" content="%s"/>
                <meta property="og:description" content="%s"/>
                <meta property="og:url" content="%s"/>
                <meta property="og:image" content="%s"/>
                <meta property="og:locale" content="en_PH"/>
                <meta name="twitter:card" content="summary_large_image"/>
                <meta name="twitter:title" content="%s"/>
                <meta name="twitter:description" content="%s"/>
                <meta name="twitter:image" content="%s"/>
                <meta http-equiv="refresh" content="0; url=%s"/>
                </head><body>
                <p>Opening this flood report on <a href="%s">FloodedArea</a>…</p>
                </body></html>
                """.formatted(t, d, t, d, u, img, t, d, img, u, u);
    }

    /** Minimal HTML-attribute escaping so a report's name/district can't break out of the meta tags. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
