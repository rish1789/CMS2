package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingAttemptOutcome;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.ClinicBookingLimitOverride;
import com.cms.booking.exception.BookingLimitReachedException;
import com.cms.booking.exception.RateLimitedException;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention (research.md Decision 3): the two synchronous booking-creation
 * preconditions - the rate limit (FR-007-FR-012) and the active-appointment limit
 * (FR-001-FR-006) - live inside {@code booking}, not the new {@code protection} module, to avoid
 * a module dependency cycle (protection already depends one-way on booking's data).
 *
 * <p>research.md Decision 6: {@link #checkAndRecordAttempt} runs rate-limit-check before
 * booking-limit-check - a patient currently in cooldown always sees the cooldown outcome, even if
 * the attempt would also have failed the appointment limit (Clarifications Q1).
 *
 * <p>Attempt recording (FR-008 - every attempt counts, whatever the outcome), per research.md
 * Decision 1: under a row lock on the patient's account, {@link #checkAndRecordAttempt} reads the
 * attempt count and inserts this attempt's own row, as OTHER_FAILURE, before any booking work
 * runs; the caller flips it via {@link #recordSuccess} once the booking exists. Both happen in
 * the caller's transaction on one connection: the lock is held until that transaction ends, so
 * the next attempt from the same patient always counts the row.
 *
 * <p>A rejection (RATE_LIMITED/LIMIT_REACHED) or a booking that fails rolls that transaction back,
 * row included, so the caller records it afterwards via {@link #recordAfterRollback}. Nothing here
 * opens a second connection while the lock is held: a burst of same-patient requests larger than
 * the connection pool would otherwise stall every one of them waiting for a connection.
 */
@Service
public class BookingProtectionService {

    private final BookingAttemptLogRepository attemptLogRepository;
    private final BookingRepository bookingRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final ClinicBookingLimitOverrideRepository clinicBookingLimitOverrideRepository;
    private final ProtectionSettingService protectionSettingService;
    private final BookingAttemptRecorder attemptRecorder;

    public BookingProtectionService(
            BookingAttemptLogRepository attemptLogRepository,
            BookingRepository bookingRepository,
            PatientAccountRepository patientAccountRepository,
            ClinicBookingLimitOverrideRepository clinicBookingLimitOverrideRepository,
            ProtectionSettingService protectionSettingService,
            BookingAttemptRecorder attemptRecorder) {
        this.attemptLogRepository = attemptLogRepository;
        this.bookingRepository = bookingRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.clinicBookingLimitOverrideRepository = clinicBookingLimitOverrideRepository;
        this.protectionSettingService = protectionSettingService;
        this.attemptRecorder = attemptRecorder;
    }

    /**
     * Throws {@link RateLimitedException}/{@link BookingLimitReachedException} without recording
     * them (see the class Javadoc); otherwise returns the admitted attempt's log id.
     */
    @Transactional
    public UUID checkAndRecordAttempt(UUID patientAccountId, UUID clinicId) {
        // research.md Decision 1: a row lock on the patient's own account serializes simultaneous
        // attempts from the same patient, so each count below sees every earlier attempt's row.
        patientAccountRepository.findWithLockById(patientAccountId);
        checkRateLimit(patientAccountId, clinicId);
        checkBookingLimit(patientAccountId, clinicId);
        return attemptRecorder.recordAdmitted(patientAccountId, clinicId);
    }

    private void checkRateLimit(UUID patientAccountId, UUID clinicId) {
        if (!protectionSettingService.isRateLimitEnabled()) {
            return;
        }
        int maxAttempts = protectionSettingService.getRateLimitMaxAttempts();
        int windowMinutes = protectionSettingService.getRateLimitWindowMinutes();
        int cooldownMinutes = protectionSettingService.getRateLimitCooldownMinutes();
        Instant now = Instant.now();

        Optional<BookingAttemptLog> lastNonRateLimited = attemptLogRepository
                .findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(
                        patientAccountId, BookingAttemptOutcome.RATE_LIMITED);
        Instant boundary = lastNonRateLimited.map(BookingAttemptLog::getAttemptedAt).orElse(Instant.EPOCH);

        Optional<BookingAttemptLog> currentCooldownTrigger = attemptLogRepository
                .findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        patientAccountId, BookingAttemptOutcome.RATE_LIMITED, boundary);

        if (currentCooldownTrigger.isPresent()) {
            Instant cooldownEnd = currentCooldownTrigger.get().getAttemptedAt().plus(cooldownMinutes, ChronoUnit.MINUTES);
            if (now.isBefore(cooldownEnd)) {
                throw new RateLimitedException(ChronoUnit.SECONDS.between(now, cooldownEnd));
            }
        }

        long attemptsInWindow = attemptLogRepository.countByPatientAccount_IdAndAttemptedAtAfter(
                patientAccountId, now.minus(windowMinutes, ChronoUnit.MINUTES));
        if (attemptsInWindow >= maxAttempts) {
            throw new RateLimitedException(cooldownMinutes * 60L);
        }
    }

    private void checkBookingLimit(UUID patientAccountId, UUID clinicId) {
        if (!protectionSettingService.isBookingLimitEnabled()) {
            return;
        }
        // The patient row lock taken in checkAndRecordAttempt also covers this count: on the
        // fixed-time path it is held until the booking commits, so the next attempt sees it.
        int globalCap = protectionSettingService.getGlobalMaxActiveAppointments();
        long globalCount = bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE);
        if (globalCount >= globalCap) {
            throw new BookingLimitReachedException();
        }

        Optional<ClinicBookingLimitOverride> override = clinicBookingLimitOverrideRepository.findByClinic_Id(clinicId);
        if (override.isPresent()) {
            long clinicCount = bookingRepository.countByPatient_PatientAccount_IdAndStatusAndPatient_Clinic_Id(
                    patientAccountId, BookingStatus.ACTIVE, clinicId);
            if (clinicCount >= override.get().getMaxActiveAppointments()) {
                throw new BookingLimitReachedException();
            }
        }
    }

    /** Flips an admitted attempt's row (see {@link #checkAndRecordAttempt}) once its booking exists. */
    @Transactional
    public void recordSuccess(UUID attemptId, Booking booking) {
        attemptLogRepository.findById(attemptId).orElseThrow().markSucceeded(booking);
    }

    /**
     * Records an attempt whose own transaction rolled back (see the class Javadoc) - call it only
     * once that transaction has ended, so its lock and connection are already released.
     */
    public void recordAfterRollback(UUID patientAccountId, UUID clinicId, RuntimeException failure) {
        BookingAttemptOutcome outcome = failure instanceof RateLimitedException
                ? BookingAttemptOutcome.RATE_LIMITED
                : failure instanceof BookingLimitReachedException
                        ? BookingAttemptOutcome.LIMIT_REACHED
                        : BookingAttemptOutcome.OTHER_FAILURE;
        attemptRecorder.record(patientAccountId, clinicId, outcome, null);
    }
}
