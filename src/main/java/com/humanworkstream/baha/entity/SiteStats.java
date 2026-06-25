package com.humanworkstream.baha.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * Singleton counter row (id is pinned to 1) holding the all-time site visit tally. A single row
 * keeps increments atomic and contention-free. {@code updated_at} is maintained entirely by the
 * DB (DEFAULT now() + BEFORE UPDATE trigger), so the app never writes it.
 */
@Entity
@Table(name = "site_stats")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SiteStats {

    @Id
    private Short id;

    @Column(nullable = false)
    private Long visits;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime updatedAt;
}
