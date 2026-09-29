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
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
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
 * <p>Attempt recording (FR-008 - every attempt counts, whatever the outcome) is split in two:
 * a rejection here (RATE_LIMITED/LIMIT_REACHED) is recorded immediately, since the outcome is
 * already known; a completion (SUCCESS/OTHER_FAILURE) is recorded by the caller via {@link
 * #recordCompletion} once the rest of {@code bookSlot} has run its course - this method cannot
 * know that outcome yet, since it runs before the slot lookup/booking creation even begins.
 */
@Service
public class BookingProtectionService {

    private final BookingAttemptLogRepository attemptLogRepository;
    private final BookingRepository bookingRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final ClinicBookingLimitOverrideRepository clinicBookingLimitOverrideRepository;
    private final ClinicRepository clinicRepository;
    private final ProtectionSettingService protectionSettingService;
    private final BookingAttemptRecorder attemptRecorder;

    public BookingProtectionService(
            BookingAttemptLogRepository attemptLogRepository,
            BookingRepository bookingRepository,
            PatientAccountRepository patientAccountRepository,
            ClinicBookingLimitOverrideRepository clinicBookingLimitOverrideRepository,
            ClinicRepository clinicRepository,
            ProtectionSettingService protectionSettingService,
            BookingAttemptRecorder attemptRecorder) {
        this.attemptLogRepository = attemptLogRepository;
        this.bookingRepository = bookingRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.clinicBookingLimitOverrideRepository = clinicBookingLimitOverrideRepository;
        this.clinicRepository = clinicRepository;
        this.protectionSettingService = protectionSettingService;
        this.attemptRecorder = attemptRecorder;
    }

    @Transactional
    public void checkAndRecordAttempt(UUID patientAccountId, UUID clinicId) {
        checkRateLimit(patientAccountId, clinicId);
        checkBookingLimit(patientAccountId, clinicId);
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
                recordRejection(patientAccountId, clinicId, BookingAttemptOutcome.RATE_LIMITED, now);
                throw new RateLimitedException(ChronoUnit.SECONDS.between(now, cooldownEnd));
            }
        }

        long attemptsInWindow = attemptLogRepository.countByPatientAccount_IdAndAttemptedAtAfter(
                patientAccountId, now.minus(windowMinutes, ChronoUnit.MINUTES));
        if (attemptsInWindow >= maxAttempts) {
            recordRejection(patientAccountId, clinicId, BookingAttemptOutcome.RATE_LIMITED, now);
            throw new RateLimitedException(cooldownMinutes * 60L);
        }
    }

    private void checkBookingLimit(UUID patientAccountId, UUID clinicId) {
        if (!protectionSettingService.isBookingLimitEnabled()) {
            return;
        }
        // research.md Decision 1: a row lock on the patient's own account serializes two
        // simultaneous booking attempts from the same patient for the rest of this transaction,
        // so the second always sees the first's freshly-inserted Booking when it re-reads the count.
        patientAccountRepository.findWithLockById(patientAccountId);

        int globalCap = protectionSettingService.getGlobalMaxActiveAppointments();
        long globalCount = bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE);
        if (globalCount >= globalCap) {
            recordRejection(patientAccountId, clinicId, BookingAttemptOutcome.LIMIT_REACHED, Instant.now());
            throw new BookingLimitReachedException();
        }

        Optional<ClinicBookingLimitOverride> override = clinicBookingLimitOverrideRepository.findByClinic_Id(clinicId);
        if (override.isPresent()) {
            long clinicCount = bookingRepository.countByPatient_PatientAccount_IdAndStatusAndPatient_Clinic_Id(
                    patientAccountId, BookingStatus.ACTIVE, clinicId);
            if (clinicCount >= override.get().getMaxActiveAppointments()) {
                recordRejection(patientAccountId, clinicId, BookingAttemptOutcome.LIMIT_REACHED, Instant.now());
                throw new BookingLimitReachedException();
            }
        }
    }

    /**
     * research.md's own concurrency reasoning requires this write to survive even though the
     * caller throws immediately after - {@link BookingAttemptRecorder#record} runs in its own
     * {@code REQUIRES_NEW} transaction for exactly that reason (see its own Javadoc).
     */
    private void recordRejection(UUID patientAccountId, UUID clinicId, BookingAttemptOutcome outcome, Instant now) {
        attemptRecorder.record(patientAccountId, clinicId, outcome, null);
    }

    /**
     * FR-008: called by {@code PatientBookingService.bookSlot}/{@code
     * PatientQueueBookingService.bookSlot} once the rest of the method has run its course -
     * records the attempt's final outcome (SUCCESS, with the created Booking, or OTHER_FAILURE
     * for any pre-existing failure mode this feature doesn't own, e.g. slot already taken). Unlike
     * a rejection, a completion is recorded as part of the same transaction as the booking itself
     * (or its failure) - there is nothing to protect it from here, since {@code bookSlot}'s own
     * transaction boundary already governs whether it commits.
     */
    public void recordCompletion(UUID patientAccountId, UUID clinicId, BookingAttemptOutcome outcome, Booking booking) {
        PatientAccount patientAccount = patientAccountRepository.getReferenceById(patientAccountId);
        Clinic clinic = clinicRepository.getReferenceById(clinicId);
        attemptLogRepository.save(new BookingAttemptLog(patientAccount, clinic, Instant.now(), outcome, booking));
    }
}
