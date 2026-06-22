package com.humanworkstream.baha.entity;

import com.humanworkstream.baha.enumeration.FeedbackStatus;
import com.humanworkstream.baha.enumeration.FeedbackType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * A logged-in user's bug report or feature request. Admins accept feature requests in the console;
 * accepted features are shown to all registered users. {@code submitterName} snapshots the author.
 */
@Entity
@Table(name = "feedback")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "feedback_type_t")
    private FeedbackType type;

    @Column(nullable = false)
    private String title;

    private String details;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(nullable = false, columnDefinition = "feedback_status_t")
    private FeedbackStatus status;

    @Column(name = "submitted_by_user_id")
    private Integer submittedByUserId;

    @Column(name = "submitter_name")
    private String submitterName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = FeedbackStatus.pending;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
