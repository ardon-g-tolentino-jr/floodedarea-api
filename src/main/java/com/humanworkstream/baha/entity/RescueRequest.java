package com.humanworkstream.baha.entity;

import com.humanworkstream.baha.enumeration.RescueStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/** An SOS rescue request ("send rescue request with my location"). */
@Entity
@Table(name = "rescue_request")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RescueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    private String note;

    /** Sender's district, reverse-geocoded client-side; powers the per-district rescue chart. */
    private String district;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "rescue_status_t")
    private RescueStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** When this request was last (re)submitted by the sender; anchors the dedup window. */
    @Column(name = "last_requested_at", nullable = false)
    private OffsetDateTime lastRequestedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (lastRequestedAt == null) lastRequestedAt = now;
        if (status == null) status = RescueStatus.pending;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
