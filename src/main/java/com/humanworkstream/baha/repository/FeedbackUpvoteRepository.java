package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FeedbackUpvote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FeedbackUpvoteRepository extends JpaRepository<FeedbackUpvote, Integer> {

    boolean existsByFeedbackIdAndUserId(Integer feedbackId, Integer userId);

    void deleteByFeedbackIdAndUserId(Integer feedbackId, Integer userId);

    long countByFeedbackId(Integer feedbackId);

    /** Upvote totals grouped by feedback id — one query to decorate a whole list. */
    @Query("SELECT u.feedbackId AS feedbackId, COUNT(u) AS total FROM FeedbackUpvote u GROUP BY u.feedbackId")
    List<UpvoteCount> countsGrouped();

    /** The feedback ids the given user has upvoted — for the per-caller "upvotedByMe" flag. */
    @Query("SELECT u.feedbackId FROM FeedbackUpvote u WHERE u.userId = :userId")
    List<Integer> feedbackIdsUpvotedBy(@Param("userId") Integer userId);

    /** Projection for {@link #countsGrouped()}. */
    interface UpvoteCount {
        Integer getFeedbackId();
        long getTotal();
    }
}
