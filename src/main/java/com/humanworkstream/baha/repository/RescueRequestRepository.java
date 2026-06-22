package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.RescueRequest;
import com.humanworkstream.baha.enumeration.RescueStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RescueRequestRepository extends JpaRepository<RescueRequest, Integer> {
    List<RescueRequest> findAllByOrderByCreatedAtDesc();
    List<RescueRequest> findByStatusOrderByCreatedAtDesc(RescueStatus status);

    /** The sender's most recently (re)requested still-active rescue, used to dedup within a window. */
    Optional<RescueRequest> findFirstByUserIdAndStatusInOrderByLastRequestedAtDesc(
            Integer userId, Collection<RescueStatus> statuses);
}
