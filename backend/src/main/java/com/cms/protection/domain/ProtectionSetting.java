package com.cms.protection.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * 060-booking-abuse-prevention (spec.md FR-026, FR-027, FR-029, FR-030): a named,
 * Super-Admin-editable, system-wide value - this system's first runtime-editable admin setting
 * (research.md Decision 5). One row per name; absence of a row means "use the documented
 * default" (data-model.md's settings table), enforced by {@code ProtectionSettingService}, never
 * read from this repository directly.
 */
@Entity
@Table(name = "protection_setting")
public class ProtectionSetting {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String value;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    protected ProtectionSetting() {
        // JPA
    }

    public ProtectionSetting(String name, String value, Instant updatedAt, String updatedBy) {
        this.name = name;
        this.value = value;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void update(String value, Instant updatedAt, String updatedBy) {
        this.value = value;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
