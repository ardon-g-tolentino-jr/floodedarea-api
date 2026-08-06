package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.SocialPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SocialPostRepository extends JpaRepository<SocialPost, Integer> {

    List<SocialPost> findByReportId(Integer reportId);

    List<SocialPost> findTop50ByOrderByCreatedAtDesc();

    /** Rows the retry sweep should consider: not yet sent, still under the attempt budget. */
    @Query("SELECT sp FROM SocialPost sp WHERE sp.status IN ('pending','failed') AND sp.attempts < :max")
    List<SocialPost> findRetryable(@Param("max") int max);
}
