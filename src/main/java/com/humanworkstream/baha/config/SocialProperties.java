package com.humanworkstream.baha.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Facebook Page + X (Twitter) auto-post knobs bound from {@code baha.social.*}. {@code enabled}
 * is the master switch; {@code dryRun} (default true) logs the payload instead of calling the
 * network so a misconfigured deploy can never post garbage — flip it explicitly in prod once
 * real tokens are in place. Per-network on/off also lives in {@code app_setting} (see
 * AppSettingService) so an admin can kill a network without a deploy.
 */
@Component
@ConfigurationProperties(prefix = "baha.social")
@Data
public class SocialProperties {
    private boolean enabled = false;
    private boolean dryRun = true;
    private int maxAttempts = 3;
    /** This backend's own public origin (no trailing slash), e.g. https://api.floodedarea.com —
     *  used to build the absolute photo URL / share-page link Facebook fetches. Detached from any
     *  request (the publish itself runs async, and the retry sweep runs on a timer), so it can't
     *  be derived per-request the way {@code ShareHtmlService} does for the synchronous /share
     *  endpoint. Blank = Facebook posting with a photo/link-card is skipped (X posting doesn't
     *  need it — photo bytes are read straight from the DB and the deep link comes from
     *  baha.public-base-url). */
    private String apiBaseUrl = "";

    private final Fb fb = new Fb();
    private final X x = new X();

    @Data
    public static class Fb {
        private String pageId = "";
        private String pageToken = "";
        private String graphVersion = "v21.0";
    }

    @Data
    public static class X {
        private String apiKey = "";
        private String apiSecret = "";
        private String accessToken = "";
        private String accessSecret = "";
    }
}
