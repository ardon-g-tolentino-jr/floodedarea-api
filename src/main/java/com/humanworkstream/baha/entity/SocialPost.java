package com.humanworkstream.baha.entity;

import com.humanworkstream.baha.enumeration.SocialNetwork;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One attempt to publish an admin "system" flood report to a social network (Facebook Page or
 * X). Status starts {@code pending}, then moves to {@code sent}, {@code failed} (retryable, up to
 * {@code baha.social.max-attempts}), or {@code skipped} (network disabled / no credentials —
 * never retried). A (report, network) pair is unique — re-publishing an already-posted report is
 * a no-op guarded by the DB, not application logic.
 */
@Entity
@Table(name = "social_post")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SocialPost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "report_id", nullable = false)
    private Integer reportId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SocialNetwork network;

    /** pending | sent | failed | skipped */
    @Column(nullable = false, length = 16)
    private String status = "pending";

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "external_url")
    private String externalUrl;

    @Column
    private String caption;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = "pending";
        if (attempts == null) attempts = 0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
