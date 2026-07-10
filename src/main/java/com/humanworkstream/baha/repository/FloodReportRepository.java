package com.humanworkstream.baha.repository;

import com.humanworkstream.baha.entity.FloodReport;
import com.humanworkstream.baha.enumeration.ReportStatus;
import com.humanworkstream.baha.enumeration.Severity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FloodReportRepository extends JpaRepository<FloodReport, Integer> {
    List<FloodReport> findByStatusOrderByCreatedAtDesc(ReportStatus status);
    List<FloodReport> findByStatusAndSeverityOrderByCreatedAtDesc(ReportStatus status, Severity severity);
    List<FloodReport> findAllByOrderByCreatedAtDesc();

    /** Reports created within a window (any status) — powers the water-level history chart. */
    List<FloodReport> findByCreatedAtBetweenOrderByCreatedAtAsc(OffsetDateTime from, OffsetDateTime to);

    /** Write the photo bytes + mime together (satisfies the DB pairing constraint). */
    @Modifying
    @Query(value = "UPDATE flood_report SET photo = :bytes, photo_mime = :mime WHERE id = :id",
           nativeQuery = true)
    void setPhoto(@Param("id") Integer id, @Param("bytes") byte[] bytes, @Param("mime") String mime);

    /** Read the photo bytes + mime for one report; empty when it has no photo. */
    @Query(value = "SELECT photo AS photo, photo_mime AS photoMime FROM flood_report " +
                   "WHERE id = :id AND photo IS NOT NULL", nativeQuery = true)
    Optional<PhotoView> findPhotoById(@Param("id") Integer id);

    /** Projection over the native photo read (Spring maps column aliases → getters). */
    interface PhotoView {
        byte[] getPhoto();
        String getPhotoMime();
    }
}
