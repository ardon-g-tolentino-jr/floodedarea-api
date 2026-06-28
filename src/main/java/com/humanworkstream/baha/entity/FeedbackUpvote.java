package com.humanworkstream.baha.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * One person's upvote on a {@link Feedback} item. The (feedback_id, user_id) pair is unique, so a
 * caller — full account or no-login guest, keyed by their user id — counts once. Re-voting is a
 * toggle (delete the row). The feedback's total upvotes is the count of these rows.
 */
@Entity
@Table(name = "feedback_upvote",
        uniqueConstraints = @UniqueConstraint(name = "uq_feedback_upvote", columnNames = {"feedback_id", "user_id"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackUpvote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "feedback_id", nullable = false)
    private Integer feedbackId;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}
