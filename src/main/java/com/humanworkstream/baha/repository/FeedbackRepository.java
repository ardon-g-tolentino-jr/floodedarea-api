package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.enumeration.FeedbackStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {
    /** Accepted items of any type — the public board (re-sorted by upvotes in the service). */
    List<Feedback> findByStatusOrderByCreatedAtDesc(FeedbackStatus status);
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
