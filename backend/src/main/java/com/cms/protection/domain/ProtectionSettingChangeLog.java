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
 * 060-booking-abuse-prevention (spec.md AUD-002, research.md Decision 9): append-only audit
 * history for {@link ProtectionSetting}. Lives in {@code protection}, the same module that owns
 * the table it audits. Null {@code previousValue} means this row records a setting's first-ever
 * write (no prior row existed - the "previous" state was the documented default).
 */
@Entity
@Table(name = "protection_setting_change_log")
public class ProtectionSettingChangeLog {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "setting_name", nullable = false)
    private String settingName;

    @Column(name = "previous_value")
    private String previousValue;

    @Column(name = "new_value", nullable = false)
    private String newValue;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    protected ProtectionSettingChangeLog() {
        // JPA
    }

    public ProtectionSettingChangeLog(
            String settingName, String previousValue, String newValue, Instant changedAt, String changedBy) {
        this.settingName = settingName;
        this.previousValue = previousValue;
        this.newValue = newValue;
        this.changedAt = changedAt;
        this.changedBy = changedBy;
    }

    public UUID getId() {
        return id;
    }

    public String getSettingName() {
        return settingName;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }
}
