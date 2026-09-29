package com.cms.booking.service;

import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (spec.md SEC-005): {@code BookingAttemptLog} rows are retained
 * only as long as the longest currently-active window needs them (the rate limiter's own window,
 * or any flagging signal that reads attempt history), then age out - unlike the two audit-history
 * tables (data-model.md), which are kept indefinitely by design. Runs alongside {@code
 * FlagDetectionService}'s own sweep, on the same daily cadence, not a new scheduling mechanism.
 */
@Service
public class BookingAttemptLogRetentionService {

    private final BookingAttemptLogRepository attemptLogRepository;
    private final ProtectionSettingService protectionSettingService;

    public BookingAttemptLogRetentionService(
            BookingAttemptLogRepository attemptLogRepository, ProtectionSettingService protectionSettingService) {
        this.attemptLogRepository = attemptLogRepository;
        this.protectionSettingService = protectionSettingService;
    }

    @Scheduled(fixedRateString = "86400000")
    @Transactional
    public void purgeExpiredAttemptLogs() {
        long longestWindowMinutes = longestActiveWindowMinutes();
        Instant cutoff = Instant.now().minus(longestWindowMinutes, ChronoUnit.MINUTES);
        attemptLogRepository.deleteByAttemptedAtBefore(cutoff);
    }

    /** The longest window any currently-configured check reads this table over - the rate limiter's own window, or FR-016/FR-020's signal windows. */
    private long longestActiveWindowMinutes() {
        long rateLimitWindow = protectionSettingService.getRateLimitWindowMinutes();
        long highAttemptVolumeWindow = protectionSettingService.getHighAttemptVolumeWindowMinutes();
        long repeatedRateLimitViolationsWindow = protectionSettingService.getRepeatedRateLimitViolationsWindowHours() * 60L;
        return Math.max(rateLimitWindow, Math.max(highAttemptVolumeWindow, repeatedRateLimitViolationsWindow));
    }
}
