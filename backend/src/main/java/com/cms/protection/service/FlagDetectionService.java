package com.cms.protection.service;

import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.domain.SuspiciousActivitySignalType;
import com.cms.protection.domain.SuspiciousActivityFlagStatus;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (spec.md FR-013-FR-020): evaluates the five admin-flagging
 * signals on a periodic sweep, one-way reading {@code booking}'s data (research.md Decision 3) -
 * never called from, and never calling into, {@code booking} or {@code scheduling}. Mirrors
 * {@code scheduling.NoShowDetectionService}'s own established periodic-sweep shape (research.md
 * Decision 4) - no event bus, no listener, nothing this codebase doesn't already use elsewhere.
 *
 * <p>Every signal method independently reads {@link ProtectionSettingService}'s current
 * thresholds/windows and is a no-op when {@code flagging.enabled} is off (FR-027). Each creates
 * at most one OUTSTANDING flag per (patient, clinic, signal type) - {@link
 * SuspiciousActivityFlagRepository}'s dedup check, backed by the database's own partial unique
 * index (V37), is consulted before every write (Clarifications).
 */
@Service
public class FlagDetectionService {

    private final BookingAttemptLogRepository attemptLogRepository;
    private final BookingRepository bookingRepository;
    private final SuspiciousActivityFlagRepository flagRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final ClinicRepository clinicRepository;
    private final ProtectionSettingService protectionSettingService;

    public FlagDetectionService(
            BookingAttemptLogRepository attemptLogRepository,
            BookingRepository bookingRepository,
            SuspiciousActivityFlagRepository flagRepository,
            PatientAccountRepository patientAccountRepository,
            ClinicRepository clinicRepository,
            ProtectionSettingService protectionSettingService) {
        this.attemptLogRepository = attemptLogRepository;
        this.bookingRepository = bookingRepository;
        this.flagRepository = flagRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.clinicRepository = clinicRepository;
        this.protectionSettingService = protectionSettingService;
    }

    /** research.md Decision 4: hourly, matching NoShowDetectionService's own cadence precedent. */
    @Scheduled(fixedRateString = "3600000")
    @Transactional
    public void runSweep() {
        if (!protectionSettingService.isFlaggingEnabled()) {
            return;
        }
        Instant now = Instant.now();
        detectHighAttemptVolume(now);
        detectRepeatedCancellations(now);
        detectRepeatedNoShows(now);
        detectOverlappingAppointments(now);
        detectRepeatedRateLimitViolations(now);
    }

    /** FR-016: raw attempt volume, independent of whether the rate limiter itself was ever triggered. */
    void detectHighAttemptVolume(Instant now) {
        int threshold = protectionSettingService.getHighAttemptVolumeThreshold();
        int windowMinutes = protectionSettingService.getHighAttemptVolumeWindowMinutes();
        Instant since = now.minus(windowMinutes, ChronoUnit.MINUTES);
        for (BookingAttemptLogRepository.PatientClinicCount row : attemptLogRepository.countAttemptsByPatientAndClinicSince(since)) {
            if (row.getCount() >= threshold) {
                createFlagIfNotDuplicate(
                        row.getPatientAccountId(),
                        row.getClinicId(),
                        SuspiciousActivitySignalType.HIGH_ATTEMPT_VOLUME,
                        row.getCount() + " booking attempts in the last " + windowMinutes + " minutes",
                        now);
            }
        }
    }

    /** FR-017: cancellations within a rolling window - reads Booking.cancelledAt (V38). */
    void detectRepeatedCancellations(Instant now) {
        int threshold = protectionSettingService.getRepeatedCancellationsThreshold();
        int windowDays = protectionSettingService.getRepeatedCancellationsWindowDays();
        Instant since = now.minus(windowDays, ChronoUnit.DAYS);
        for (BookingRepository.PatientClinicCount row : bookingRepository.countCancellationsByPatientAndClinicSince(since)) {
            if (row.getCount() >= threshold) {
                createFlagIfNotDuplicate(
                        row.getPatientAccountId(),
                        row.getClinicId(),
                        SuspiciousActivitySignalType.REPEATED_CANCELLATIONS,
                        row.getCount() + " cancellations in the last " + windowDays + " days",
                        now);
            }
        }
    }

    /** FR-018: no-shows within a rolling window - read-only, never feeds back into scheduling (BR-007). */
    void detectRepeatedNoShows(Instant now) {
        int threshold = protectionSettingService.getRepeatedNoShowsThreshold();
        int windowDays = protectionSettingService.getRepeatedNoShowsWindowDays();
        LocalDate since = LocalDate.now().minusDays(windowDays);
        for (BookingRepository.PatientClinicCount row : bookingRepository.countNoShowsByPatientAndClinicSince(since)) {
            if (row.getCount() >= threshold) {
                createFlagIfNotDuplicate(
                        row.getPatientAccountId(),
                        row.getClinicId(),
                        SuspiciousActivitySignalType.REPEATED_NO_SHOWS,
                        row.getCount() + " no-shows in the last " + windowDays + " days",
                        now);
            }
        }
    }

    /**
     * FR-019: {@code threshold} or more of a patient's own active Fixed-Time bookings whose
     * [startTime, endTime) ranges overlap on the same date - flagged at every clinic involved in
     * the overlapping set (each has a genuine stake in "this patient double-booked with us").
     * Recent bookings only (a rolling 90-day forward+back window is generous enough to catch any
     * realistic overlap without scanning the whole table).
     */
    void detectOverlappingAppointments(Instant now) {
        int threshold = protectionSettingService.getOverlappingAppointmentsThreshold();
        LocalDate since = LocalDate.now().minusDays(90);
        Map<UUID, List<BookingRepository.ActiveBookingSchedule>> byPatient = new HashMap<>();
        for (BookingRepository.ActiveBookingSchedule row : bookingRepository.findActiveFixedTimeSchedulesSince(since)) {
            byPatient.computeIfAbsent(row.getPatientAccountId(), k -> new ArrayList<>()).add(row);
        }
        for (Map.Entry<UUID, List<BookingRepository.ActiveBookingSchedule>> entry : byPatient.entrySet()) {
            List<BookingRepository.ActiveBookingSchedule> schedules = entry.getValue();
            Set<BookingRepository.ActiveBookingSchedule> overlapping = new HashSet<>();
            for (int i = 0; i < schedules.size(); i++) {
                for (int j = i + 1; j < schedules.size(); j++) {
                    if (overlaps(schedules.get(i), schedules.get(j))) {
                        overlapping.add(schedules.get(i));
                        overlapping.add(schedules.get(j));
                    }
                }
            }
            if (overlapping.size() >= threshold) {
                Set<UUID> involvedClinics = overlapping.stream()
                        .map(BookingRepository.ActiveBookingSchedule::getClinicId)
                        .collect(java.util.stream.Collectors.toSet());
                for (UUID clinicId : involvedClinics) {
                    createFlagIfNotDuplicate(
                            entry.getKey(),
                            clinicId,
                            SuspiciousActivitySignalType.OVERLAPPING_APPOINTMENTS,
                            overlapping.size() + " active appointments with overlapping times",
                            now);
                }
            }
        }
    }

    private boolean overlaps(BookingRepository.ActiveBookingSchedule a, BookingRepository.ActiveBookingSchedule b) {
        if (!a.getSessionDate().equals(b.getSessionDate())) {
            return false;
        }
        LocalTime aStart = a.getStartTime();
        LocalTime aEnd = a.getEndTime();
        LocalTime bStart = b.getStartTime();
        LocalTime bEnd = b.getEndTime();
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /**
     * FR-020: distinct cooldown episodes, not raw RATE_LIMITED row count - rows within the
     * current {@code rate-limit.cooldown-minutes} of the previous one belong to the same episode
     * (the same grouping BookingProtectionService itself uses to keep one cooldown "fixed").
     */
    void detectRepeatedRateLimitViolations(Instant now) {
        int threshold = protectionSettingService.getRepeatedRateLimitViolationsThreshold();
        int windowHours = protectionSettingService.getRepeatedRateLimitViolationsWindowHours();
        int cooldownMinutes = protectionSettingService.getRateLimitCooldownMinutes();
        Instant since = now.minus(windowHours, ChronoUnit.HOURS);

        Map<UUID, Map<UUID, Integer>> episodesByPatientAndClinic = new HashMap<>();
        Map<UUID, Map<UUID, Instant>> lastEpisodeStartByPatientAndClinic = new HashMap<>();
        for (BookingAttemptLog row : attemptLogRepository.findRateLimitedSince(since)) {
            UUID patientAccountId = row.getPatientAccount().getId();
            UUID clinicId = row.getClinic().getId();
            Instant lastStart = lastEpisodeStartByPatientAndClinic
                    .computeIfAbsent(patientAccountId, k -> new HashMap<>())
                    .get(clinicId);
            boolean newEpisode = lastStart == null || row.getAttemptedAt().isAfter(lastStart.plus(cooldownMinutes, ChronoUnit.MINUTES));
            if (newEpisode) {
                episodesByPatientAndClinic.computeIfAbsent(patientAccountId, k -> new HashMap<>()).merge(clinicId, 1, Integer::sum);
                lastEpisodeStartByPatientAndClinic.get(patientAccountId).put(clinicId, row.getAttemptedAt());
            }
        }

        episodesByPatientAndClinic.forEach((patientAccountId, byClinic) -> byClinic.forEach((clinicId, episodeCount) -> {
            if (episodeCount >= threshold) {
                createFlagIfNotDuplicate(
                        patientAccountId,
                        clinicId,
                        SuspiciousActivitySignalType.REPEATED_RATE_LIMIT_VIOLATIONS,
                        episodeCount + " rate-limit cooldowns in the last " + windowHours + " hours",
                        now);
            }
        }));
    }

    /** FR-014/Clarifications: at most one outstanding flag per (patient, clinic, signal type). */
    private void createFlagIfNotDuplicate(
            UUID patientAccountId, UUID clinicId, SuspiciousActivitySignalType signalType, String reason, Instant now) {
        boolean alreadyOutstanding = flagRepository
                .findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(
                        patientAccountId, clinicId, signalType, SuspiciousActivityFlagStatus.OUTSTANDING)
                .isPresent();
        if (alreadyOutstanding) {
            return;
        }
        PatientAccount patientAccount = patientAccountRepository.getReferenceById(patientAccountId);
        Clinic clinic = clinicRepository.getReferenceById(clinicId);
        flagRepository.save(new SuspiciousActivityFlag(patientAccount, clinic, signalType, reason, now));
    }
}
