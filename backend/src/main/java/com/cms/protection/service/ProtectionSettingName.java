package com.cms.protection.service;

import java.util.Arrays;
import java.util.Optional;

/**
 * 060-booking-abuse-prevention (data-model.md's settings table): the fixed, known set of
 * Super-Admin-editable setting names, each with its documented default (spec.md Assumptions) and
 * value type - the exhaustive registry {@link ProtectionSettingService} reads/writes through, so
 * "no row = default" and "unrecognized name = rejected" are enforced in exactly one place.
 */
public enum ProtectionSettingName {
    BOOKING_LIMIT_GLOBAL_MAX_ACTIVE("booking-limit.global-max-active", "15", ValueType.POSITIVE_INT),
    BOOKING_LIMIT_ENABLED("booking-limit.enabled", "true", ValueType.BOOLEAN),
    RATE_LIMIT_MAX_ATTEMPTS("rate-limit.max-attempts", "8", ValueType.POSITIVE_INT),
    RATE_LIMIT_WINDOW_MINUTES("rate-limit.window-minutes", "10", ValueType.POSITIVE_INT),
    RATE_LIMIT_COOLDOWN_MINUTES("rate-limit.cooldown-minutes", "15", ValueType.POSITIVE_INT),
    RATE_LIMIT_ENABLED("rate-limit.enabled", "true", ValueType.BOOLEAN),
    FLAGGING_ENABLED("flagging.enabled", "true", ValueType.BOOLEAN),
    HIGH_ATTEMPT_VOLUME_THRESHOLD("flagging.high-attempt-volume.threshold", "10", ValueType.POSITIVE_INT),
    HIGH_ATTEMPT_VOLUME_WINDOW_MINUTES("flagging.high-attempt-volume.window-minutes", "60", ValueType.POSITIVE_INT),
    REPEATED_CANCELLATIONS_THRESHOLD("flagging.repeated-cancellations.threshold", "4", ValueType.POSITIVE_INT),
    REPEATED_CANCELLATIONS_WINDOW_DAYS("flagging.repeated-cancellations.window-days", "30", ValueType.POSITIVE_INT),
    REPEATED_NO_SHOWS_THRESHOLD("flagging.repeated-no-shows.threshold", "3", ValueType.POSITIVE_INT),
    REPEATED_NO_SHOWS_WINDOW_DAYS("flagging.repeated-no-shows.window-days", "90", ValueType.POSITIVE_INT),
    OVERLAPPING_APPOINTMENTS_THRESHOLD("flagging.overlapping-appointments.threshold", "3", ValueType.POSITIVE_INT),
    REPEATED_RATE_LIMIT_VIOLATIONS_THRESHOLD("flagging.repeated-rate-limit-violations.threshold", "3", ValueType.POSITIVE_INT),
    REPEATED_RATE_LIMIT_VIOLATIONS_WINDOW_HOURS("flagging.repeated-rate-limit-violations.window-hours", "24", ValueType.POSITIVE_INT);

    private final String settingName;
    private final String defaultValue;
    private final ValueType type;

    ProtectionSettingName(String settingName, String defaultValue, ValueType type) {
        this.settingName = settingName;
        this.defaultValue = defaultValue;
        this.type = type;
    }

    public String settingName() {
        return settingName;
    }

    public String defaultValue() {
        return defaultValue;
    }

    public ValueType type() {
        return type;
    }

    public static Optional<ProtectionSettingName> fromSettingName(String settingName) {
        return Arrays.stream(values()).filter(n -> n.settingName.equals(settingName)).findFirst();
    }

    public enum ValueType {
        POSITIVE_INT,
        BOOLEAN
    }
}
