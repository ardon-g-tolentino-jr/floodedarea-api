package com.humanworkstream.baha.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/** A single global app setting, stored as a key/value pair (e.g. whether the community chat
 *  widget is shown). Reading is public; writing is ADMIN-only (see SecurityConfig). */
@Entity
@Table(name = "app_setting")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppSetting {

    @Id
    private String key;

    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = OffsetDateTime.now();
    }
}
