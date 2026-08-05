package com.humanworkstream.baha.entity;

import com.humanworkstream.baha.enumeration.AlertLevel;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** An admin-pinned "this specific place floods" zone (pin + radius) shown on the map. */
@Entity
@Table(name = "flood_prone_area")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FloodProneArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String district;

    @Column(nullable = false)
    private BigDecimal latitude;

    @Column(nullable = false)
    private BigDecimal longitude;

    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "alert_level_t")
    private AlertLevel level;

    private String note;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "last_warned_at")
    private OffsetDateTime lastWarnedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (level == null) level = AlertLevel.orange;
        if (active == null) active = true;
        if (radiusM == null) radiusM = 500;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
