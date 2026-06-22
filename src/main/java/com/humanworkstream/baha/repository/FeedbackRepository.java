package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.Feedback;
import com.humanworkstream.baha.enumeration.FeedbackStatus;
import com.humanworkstream.baha.enumeration.FeedbackType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {
    List<Feedback> findByTypeAndStatusOrderByCreatedAtDesc(FeedbackType type, FeedbackStatus status);
    List<Feedback> findAllByOrderByCreatedAtDesc();
}
