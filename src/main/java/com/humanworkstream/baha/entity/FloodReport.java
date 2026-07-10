package com.humanworkstream.baha.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** A crowd-sourced flood report plotted on the map. */
@Entity
@Table(name = "flood_report")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FloodReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "severity_t")
    private Severity severity;

    @Column(name = "depth_cm", nullable = false)
    private Integer depthCm;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "trend_t")
    private Trend trend;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "road_t")
    private Road road;

    @Column(name = "reports_count", nullable = false)
    private Integer reportsCount;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "report_status_t")
    private ReportStatus status;

    @Column(name = "reporter_user_id")
    private Integer reporterUserId;

    /** Author trust tier exposed to the UI: "verified" (a real account), "guest" (no-login), or
     *  "system" (an authoritative report an admin logged from news/other sources). */
    @Column(name = "reporter_trust", nullable = false)
    private String reporterTrust;

    /** When the source reported the flood (admin/system reports). Anchors the client's TTL countdown.
     *  Defaults to createdAt on persist so every report has one. */
    @Column(name = "reported_at")
    private OffsetDateTime reportedAt;

    /** Admin-defined lifetime in minutes (system reports); null ⇒ the client's severity default. */
    @Column(name = "ttl_minutes")
    private Integer ttlMinutes;

    /** Presence + type of the report's photo. Read-only here: the bytes and this mime are written
     *  together by a native UPDATE (see FloodReportRepository.setPhoto). Never serialized directly. */
    @JsonIgnore
    @Column(name = "photo_mime", insertable = false, updatable = false)
    private String photoMime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = ReportStatus.active;
        if (trend == null) trend = Trend.steady;
        if (road == null) road = Road.risky;
        if (reportsCount == null) reportsCount = 1;
        if (reporterTrust == null) reporterTrust = "verified";
        // Every report gets a reportedAt so the client can always anchor its TTL countdown.
        if (reportedAt == null) reportedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    /** URL the UI uses to load the photo, or null when none. Derived — not a column. */
    @Transient
    public String getPhotoUrl() {
        return photoMime != null ? "/reports/" + id + "/photo" : null;
    }
}
