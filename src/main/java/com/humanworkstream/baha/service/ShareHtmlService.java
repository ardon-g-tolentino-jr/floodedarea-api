package com.humanworkstream.baha.service;

import com.humanworkstream.baha.entity.FloodReport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Renders the tiny HTML page social-media crawlers (Facebook, X, WhatsApp, …) read to build a link
 * preview. Crawlers don't run JavaScript, so the SPA's runtime state is invisible to them; the share
 * buttons point crawlers at {@code GET /reports/{id}/share} (this page) so the preview card shows the
 * report's own details instead of the generic site cover. Absolute URLs (required by crawlers) are
 * built from {@code baha.public-base-url} (the site) and the request's own origin (this API).
 *
 * <p>Two rules make Facebook actually use THIS page's tags rather than the generic homepage:
 * <ul>
 *   <li>{@code og:url} is <b>self-referential</b> (points back at this /share URL). If it pointed at
 *       the deep-link, Facebook would canonicalize to that URL and scrape the SPA's generic OG.</li>
 *   <li>Humans are bounced to the app with a <b>JavaScript</b> redirect, not a {@code <meta refresh>}
 *       — the crawler doesn't run JS, so it keeps the report tags; a meta-refresh would make it follow
 *       through to the generic SPA.</li>
 * </ul>
 */
@Service
public class ShareHtmlService {

    private final String base; // public site origin, no trailing slash

    public ShareHtmlService(@Value("${baha.public-base-url}") String publicBaseUrl) {
        this.base = stripSlash(publicBaseUrl);
    }

    /**
     * Per-report Open Graph / Twitter card: severity, depth, trend, location, and the photo when
     * present. {@code apiBase} is the public origin of THIS backend (derived from the incoming
     * request) — both the self-referential {@code og:url} and the photo {@code og:image} point there.
     */
    public String renderReport(FloodReport r, String apiBase) {
        String apiOrigin = stripSlash(apiBase);
        String shareUrl = apiOrigin + "/reports/" + r.getId() + "/share"; // canonical (og:url) — keep FB on THIS page
        String deepLink = base + "/?report=" + r.getId();                 // where humans are sent
        String sev = r.getSeverity().name().toUpperCase();                // HIGH / MEDIUM / LOW
        String where = r.getDistrict() != null && !r.getDistrict().isBlank()
                ? r.getName() + " (" + r.getDistrict() + ")"
                : r.getName();
        String trend = switch (r.getTrend()) {
            case rising -> ", rising";
            case receding -> ", receding";
            case steady -> "";
        };
        String title = sev + " flooding — " + r.getName() + " · FloodedArea";
        // Where + What + (the report time answers When via "reported X ago" on the app it opens).
        String desc = "~" + r.getDepthCm() + " cm" + trend + " at " + where
                + ". Live crowd-sourced flood report on FloodedArea — real-time flood map for the Philippines.";
        // The photo is streamed by this backend — use the API's own origin so the crawler can fetch it.
        // Fall back to the site-wide cover (a static file on the public site) when there's no photo.
        String image = r.getPhotoUrl() != null
                ? apiOrigin + r.getPhotoUrl()
                : base + "/og-cover.jpg?v=1";
        return html(title, desc, image, shareUrl, deepLink);
    }

    /** Site-wide card for a missing/invalid report id (mirrors index.html's static OG tags). */
    public String renderDefault() {
        String site = base + "/";
        return html(
                "FloodedArea — Live Flood Watch for the Philippines",
                "Crowd-sourced, real-time flood map for the Philippines: live reports, evacuation centers, "
                        + "road conditions, rainfall alerts and SOS rescue.",
                base + "/og-cover.jpg?v=1",
                site,   // canonical = the site itself
                site);  // redirect humans to the site
    }

    /**
     * @param canonicalUrl the {@code og:url} — should be this page (self-referential) so Facebook uses
     *                     these tags; {@code redirectUrl} is where a human viewing this page is sent.
     */
    private String html(String title, String desc, String image, String canonicalUrl, String redirectUrl) {
        String t = esc(title);
        String d = esc(desc);
        String img = esc(image);
        String cu = esc(canonicalUrl);
        String ru = esc(redirectUrl);
        String js = jsString(redirectUrl); // for the <script> redirect (JS-string escaped, not HTML)
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
                </head><body>
                <p>Opening this flood report on <a href="%s">FloodedArea</a>…</p>
                <script>location.replace(%s)</script>
                </body></html>
                """.formatted(t, d, t, d, cu, img, t, d, img, ru, js);
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

    /** A single-quoted JS string literal for the redirect (URL is app-controlled, but stay safe). */
    private static String jsString(String url) {
        String safe = (url == null ? "" : url)
                .replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("<", "\\u003C"); // never let a "</script>" slip through
        return "'" + safe + "'";
    }

    private static String stripSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
