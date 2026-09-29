package com.cms.protection.service;

import com.cms.protection.domain.ProtectionSetting;
import com.cms.protection.domain.ProtectionSettingChangeLog;
import com.cms.protection.exception.InvalidSettingValueException;
import com.cms.protection.exception.ProtectionSettingNotFoundException;
import com.cms.protection.exception.UnrecognizedSettingException;
import com.cms.protection.repository.ProtectionSettingChangeLogRepository;
import com.cms.protection.repository.ProtectionSettingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (research.md Decision 5): this system's first runtime-editable
 * admin setting mechanism. Every read falls back to {@link ProtectionSettingName}'s documented
 * default when no row exists yet (FR-029) - reading code MUST go through this service, never the
 * repository directly, so that fallback is enforced in exactly one place. Every write appends a
 * {@link ProtectionSettingChangeLog} row in the same transaction (AUD-002).
 */
@Service
public class ProtectionSettingService {

    private final ProtectionSettingRepository settingRepository;
    private final ProtectionSettingChangeLogRepository changeLogRepository;

    public ProtectionSettingService(
            ProtectionSettingRepository settingRepository, ProtectionSettingChangeLogRepository changeLogRepository) {
        this.settingRepository = settingRepository;
        this.changeLogRepository = changeLogRepository;
    }

    private String rawValue(ProtectionSettingName name) {
        return settingRepository.findByName(name.settingName()).map(ProtectionSetting::getValue).orElse(name.defaultValue());
    }

    private int intValue(ProtectionSettingName name) {
        return Integer.parseInt(rawValue(name));
    }

    private boolean boolValue(ProtectionSettingName name) {
        return Boolean.parseBoolean(rawValue(name));
    }

    public int getGlobalMaxActiveAppointments() {
        return intValue(ProtectionSettingName.BOOKING_LIMIT_GLOBAL_MAX_ACTIVE);
    }

    public boolean isBookingLimitEnabled() {
        return boolValue(ProtectionSettingName.BOOKING_LIMIT_ENABLED);
    }

    public int getRateLimitMaxAttempts() {
        return intValue(ProtectionSettingName.RATE_LIMIT_MAX_ATTEMPTS);
    }

    public int getRateLimitWindowMinutes() {
        return intValue(ProtectionSettingName.RATE_LIMIT_WINDOW_MINUTES);
    }

    public int getRateLimitCooldownMinutes() {
        return intValue(ProtectionSettingName.RATE_LIMIT_COOLDOWN_MINUTES);
    }

    public boolean isRateLimitEnabled() {
        return boolValue(ProtectionSettingName.RATE_LIMIT_ENABLED);
    }

    public boolean isFlaggingEnabled() {
        return boolValue(ProtectionSettingName.FLAGGING_ENABLED);
    }

    public int getHighAttemptVolumeThreshold() {
        return intValue(ProtectionSettingName.HIGH_ATTEMPT_VOLUME_THRESHOLD);
    }

    public int getHighAttemptVolumeWindowMinutes() {
        return intValue(ProtectionSettingName.HIGH_ATTEMPT_VOLUME_WINDOW_MINUTES);
    }

    public int getRepeatedCancellationsThreshold() {
        return intValue(ProtectionSettingName.REPEATED_CANCELLATIONS_THRESHOLD);
    }

    public int getRepeatedCancellationsWindowDays() {
        return intValue(ProtectionSettingName.REPEATED_CANCELLATIONS_WINDOW_DAYS);
    }

    public int getRepeatedNoShowsThreshold() {
        return intValue(ProtectionSettingName.REPEATED_NO_SHOWS_THRESHOLD);
    }

    public int getRepeatedNoShowsWindowDays() {
        return intValue(ProtectionSettingName.REPEATED_NO_SHOWS_WINDOW_DAYS);
    }

    public int getOverlappingAppointmentsThreshold() {
        return intValue(ProtectionSettingName.OVERLAPPING_APPOINTMENTS_THRESHOLD);
    }

    public int getRepeatedRateLimitViolationsThreshold() {
        return intValue(ProtectionSettingName.REPEATED_RATE_LIMIT_VIOLATIONS_THRESHOLD);
    }

    public int getRepeatedRateLimitViolationsWindowHours() {
        return intValue(ProtectionSettingName.REPEATED_RATE_LIMIT_VIOLATIONS_WINDOW_HOURS);
    }

    /** FR-026: every named setting, current value, and whether it's still on its documented default (FR-029). */
    public List<SettingView> listAll() {
        return java.util.Arrays.stream(ProtectionSettingName.values())
                .map(name -> {
                    Optional<ProtectionSetting> stored = settingRepository.findByName(name.settingName());
                    return new SettingView(
                            name.settingName(),
                            stored.map(ProtectionSetting::getValue).orElse(name.defaultValue()),
                            stored.isEmpty(),
                            stored.map(ProtectionSetting::getUpdatedAt).orElse(null),
                            stored.map(ProtectionSetting::getUpdatedBy).orElse(null));
                })
                .toList();
    }

    /** FR-026/AUD-002: validates, upserts, and appends a change-log row in one transaction. */
    @Transactional
    public SettingView update(String settingName, String newValue, String changedBy) {
        ProtectionSettingName name = ProtectionSettingName.fromSettingName(settingName)
                .orElseThrow(() -> new UnrecognizedSettingException(settingName));
        validate(name, newValue);

        Instant now = Instant.now();
        Optional<ProtectionSetting> existing = settingRepository.findByName(settingName);
        String previousValue = existing.map(ProtectionSetting::getValue).orElse(null);

        ProtectionSetting setting = existing.orElseGet(() -> new ProtectionSetting(settingName, newValue, now, changedBy));
        if (existing.isPresent()) {
            setting.update(newValue, now, changedBy);
        }
        settingRepository.save(setting);
        changeLogRepository.save(new ProtectionSettingChangeLog(settingName, previousValue, newValue, now, changedBy));

        return new SettingView(settingName, newValue, false, now, changedBy);
    }

    /** AUD-002/AUD-004: the full change history for one setting, newest first. */
    public List<ProtectionSettingChangeLog> history(String settingName) {
        ProtectionSettingName.fromSettingName(settingName).orElseThrow(() -> new ProtectionSettingNotFoundException(settingName));
        return changeLogRepository.findBySettingNameOrderByChangedAtDesc(settingName);
    }

    private void validate(ProtectionSettingName name, String value) {
        try {
            switch (name.type()) {
                case POSITIVE_INT -> {
                    int parsed = Integer.parseInt(value);
                    if (parsed <= 0) {
                        throw new InvalidSettingValueException(name.settingName(), value);
                    }
                }
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new InvalidSettingValueException(name.settingName(), value);
                    }
                }
            }
        } catch (NumberFormatException e) {
            throw new InvalidSettingValueException(name.settingName(), value);
        }
    }

    public record SettingView(String name, String value, boolean isDefault, Instant updatedAt, String updatedBy) {}
}
