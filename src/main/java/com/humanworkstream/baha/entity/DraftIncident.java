package com.humanworkstream.baha.entity;

import com.humanworkstream.baha.enumeration.IncidentStatus;
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

/**
 * A flood incident the backend drafted from a news source, awaiting admin review. It carries the
 * same map fields as a flood report plus the source it came from and an extraction {@code confidence}.
 * Approving one (POST /incidents/{id}/approve) creates a system {@link FloodReport}
 * ({@code reporterTrust='system'}); the human is the gate, so a bad extraction never reaches the
 * public map on its own. Mirrors the moderation shape of {@link Feedback}.
 */
@Entity
@Table(name = "draft_incident")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DraftIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /** The news headline the draft was extracted from. */
    @Column(nullable = false)
    private String headline;

    /** Publisher name, e.g. "GMA News". */
    @Column(name = "source_name", nullable = false)
    private String sourceName;

    /** Link to the original article. Unique — the ingestion job dedupes on it. */
    @Column(name = "source_url", nullable = false)
    private String sourceUrl;

    /** When the article was published — becomes the report's reportedAt on approval. */
    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

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

    /** Extraction confidence 0..1; low values flag a draft as needing a closer look before approval. */
    @Column(nullable = false)
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "incident_status_t")
    private IncidentStatus status;

    /** The flood report created when this draft was approved; null while pending/dismissed. */
    @Column(name = "created_report_id")
    private Integer createdReportId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = IncidentStatus.pending;
        if (trend == null) trend = Trend.steady;
        if (road == null) road = Road.risky;
        if (confidence == null) confidence = 0.0;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
