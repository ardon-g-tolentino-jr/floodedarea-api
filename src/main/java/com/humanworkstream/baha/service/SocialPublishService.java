package com.humanworkstream.baha.service;

import com.humanworkstream.baha.config.SocialProperties;
import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.entity.SocialPost;
import com.humanworkstream.baha.enumeration.SocialNetwork;
import com.humanworkstream.baha.repository.FloodReportRepository;
import com.humanworkstream.baha.repository.SocialPostRepository;
import com.humanworkstream.baha.service.social.FacebookPoster;
import com.humanworkstream.baha.service.social.PostResult;
import com.humanworkstream.baha.service.social.XPoster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Publishes admin "system" flood reports to the FloodedArea Facebook Page and X account. A
 * report's {@code social_post} rows are inserted synchronously (inside the same transaction as
 * the report) so they're never lost, but the actual network calls run {@link Async} and only
 * after that transaction commits ({@link #enqueue} is called from FloodReportService's
 * after-commit hook) — a Meta/X outage must never fail or slow down POST /reports.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SocialPublishService {

    private final SocialPostRepository socialPostRepository;
    private final FloodReportRepository reportRepository;
    private final AppSettingService appSettingService;
    private final SocialProperties props;
    private final FacebookPoster facebookPoster;
    private final XPoster xPoster;

    @Value("${baha.public-base-url}")
    private String publicBaseUrl;

    /**
     * Inserts {@code pending} social_post rows for a freshly created report — called from within
     * FloodReportService.create()'s own transaction, so a rollback there also rolls this back.
     * Composes the final caption (client-supplied body, or a fallback, plus the deep link) once
     * and stores it on every row so a later retry always posts the same text.
     */
    @Transactional
    public void enqueue(FloodReport report, List<String> networks, String rawCaption) {
        if (networks == null || networks.isEmpty()) return;
        String caption = composeCaption(report, rawCaption);
        for (String raw : networks) {
            SocialNetwork network;
            try {
                network = SocialNetwork.valueOf(raw.trim().toLowerCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                log.warn("[social] ignoring unknown network '{}' for reportId={}", raw, report.getId());
                continue;
            }
            if (!socialPostRepository.findByReportId(report.getId()).stream()
                    .anyMatch(sp -> sp.getNetwork() == network)) {
                SocialPost row = new SocialPost();
                row.setReportId(report.getId());
                row.setNetwork(network);
                row.setCaption(caption);
                socialPostRepository.save(row);
            }
        }
    }

    private String composeCaption(FloodReport report, String rawCaption) {
        String body = (rawCaption != null && !rawCaption.isBlank()) ? rawCaption.trim() : fallbackCaption(report);
        String link = stripSlash(publicBaseUrl) + "/?report=" + report.getId();
        return body + "\n" + link;
    }

    /** Minimal server-side fallback when the client didn't send a caption. */
    private static String fallbackCaption(FloodReport report) {
        String sev = report.getSeverity().name().toUpperCase(Locale.ROOT);
        String where = report.getDistrict() != null && !report.getDistrict().isBlank()
                ? report.getName() + " (" + report.getDistrict() + ")"
                : report.getName();
        String trend = switch (report.getTrend()) {
            case rising -> ", rising";
            case receding -> ", receding";
            case steady -> "";
        };
        return sev + " flooding — " + where + ". ~" + report.getDepthCm() + " cm" + trend + ".";
    }

    /** Publishes every pending row for a report. Runs async and off the request thread — invoked
     *  from the after-commit hook, never inline with POST /reports. */
    @Async
    public void publish(Integer reportId) {
        Optional<FloodReport> reportOpt = reportRepository.findById(reportId);
        if (reportOpt.isEmpty()) return;
        FloodReport report = reportOpt.get();
        for (SocialPost row : socialPostRepository.findByReportId(reportId)) {
            if ("pending".equals(row.getStatus()) || "failed".equals(row.getStatus())) {
                attempt(row, report);
            }
        }
    }

    /** Every 5 minutes, retry rows still under the attempt budget (pending from a JVM restart, or
     *  failed with a retryable error). A rough backoff: skip a row until 2^attempts minutes have
     *  passed since it was last touched, so a flapping network doesn't get hammered. */
    @Scheduled(fixedDelayString = "PT5M")
    public void sweep() {
        List<SocialPost> retryable = socialPostRepository.findRetryable(props.getMaxAttempts());
        if (retryable.isEmpty()) return;
        OffsetDateTime now = OffsetDateTime.now();
        for (SocialPost row : retryable) {
            long backoffMinutes = (long) Math.pow(2, Math.max(0, row.getAttempts()));
            if (row.getUpdatedAt() != null
                    && Duration.between(row.getUpdatedAt(), now).toMinutes() < backoffMinutes) {
                continue;
            }
            reportRepository.findById(row.getReportId()).ifPresent(report -> attempt(row, report));
        }
    }

    /** Resets one row to pending (0 attempts) and republishes it immediately — the admin panel's
     *  manual Retry button. */
    @Async
    public void retry(Integer socialPostId) {
        socialPostRepository.findById(socialPostId).ifPresent(row -> {
            row.setStatus("pending");
            row.setAttempts(0);
            row.setLastError(null);
            socialPostRepository.save(row);
            reportRepository.findById(row.getReportId()).ifPresent(report -> attempt(row, report));
        });
    }

    private void attempt(SocialPost row, FloodReport report) {
        if (!props.isEnabled()) {
            markSkipped(row, "Social auto-post disabled (baha.social.enabled=false)");
            return;
        }
        boolean networkOn = row.getNetwork() == SocialNetwork.facebook
                ? appSettingService.isEnabled("social_autopost_facebook")
                : appSettingService.isEnabled("social_autopost_x");
        if (!networkOn) {
            markSkipped(row, "Auto-post to " + row.getNetwork() + " disabled in Admin settings");
            return;
        }

        // Dry run logs the exact payload and marks the row 'skipped' — NOT 'sent'. Nothing reached
        // the network, so claiming success would both lie to the admin panel and make the row
        // ineligible for a real publish once tokens are live (the admin's Retry resets it).
        if (props.isDryRun()) {
            log.info("[social] dry-run {} reportId={} caption={}", row.getNetwork(), report.getId(), row.getCaption());
            markSkipped(row, "Dry run — payload logged, nothing posted (baha.social.dry-run=true)");
            return;
        }

        row.setAttempts(row.getAttempts() + 1);

        PostResult result = switch (row.getNetwork()) {
            case facebook -> publishFacebook(row, report);
            case x -> publishX(row, report);
        };

        if (result.success()) {
            row.setStatus("sent");
            row.setExternalId(result.externalId());
            row.setExternalUrl(result.externalUrl());
            row.setLastError(null);
            log.info("[social] {} sent reportId={} externalUrl={}", row.getNetwork(), report.getId(), result.externalUrl());
        } else {
            row.setLastError(result.error());
            if (result.retryable() && row.getAttempts() < props.getMaxAttempts()) {
                row.setStatus("pending"); // eligible for the next sweep
            } else {
                row.setStatus("failed");
            }
            log.warn("[social] {} failed reportId={} attempt={} retryable={} error={}",
                    row.getNetwork(), report.getId(), row.getAttempts(), result.retryable(), result.error());
        }
        socialPostRepository.save(row);
    }

    private PostResult publishFacebook(SocialPost row, FloodReport report) {
        String apiOrigin = stripSlash(props.getApiBaseUrl());
        if (apiOrigin.isBlank()) {
            return PostResult.permanentFailure("baha.social.api-base-url not configured");
        }
        return facebookPoster.post(report, row.getCaption(), apiOrigin);
    }

    private PostResult publishX(SocialPost row, FloodReport report) {
        byte[] photoBytes = null;
        String photoMime = null;
        if (report.getPhotoUrl() != null) {
            var photo = reportRepository.findPhotoById(report.getId()).orElse(null);
            if (photo != null) {
                photoBytes = photo.getPhoto();
                photoMime = photo.getPhotoMime();
            }
        }
        return xPoster.post(report, row.getCaption(), photoBytes, photoMime);
    }

    private void markSkipped(SocialPost row, String reason) {
        row.setStatus("skipped");
        row.setLastError(reason);
        socialPostRepository.save(row);
        log.info("[social] {} skipped reportId={}: {}", row.getNetwork(), row.getReportId(), reason);
    }

    private static String stripSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }
}
