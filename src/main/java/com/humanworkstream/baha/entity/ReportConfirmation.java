package com.humanworkstream.baha.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.OffsetDateTime;

/** One "confirm" per user per report — backs {@code flood_report.reports_count} and prevents dupes. */
@Entity
@Table(name = "report_confirmation")
@IdClass(ReportConfirmation.Key.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportConfirmation {

    @Id
    @Column(name = "report_id")
    private Integer reportId;

    @Id
    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    /** Composite key (report_id, user_id). */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        private Integer reportId;
        private Integer userId;
    }
}
