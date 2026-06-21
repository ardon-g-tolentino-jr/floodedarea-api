package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.ReportConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportConfirmationRepository
        extends JpaRepository<ReportConfirmation, ReportConfirmation.Key> {
    boolean existsByReportIdAndUserId(Integer reportId, Integer userId);
}
