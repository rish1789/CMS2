package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingAttemptOutcome;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 060-booking-abuse-prevention: a separate bean (not a private method on {@link
 * BookingProtectionService}) specifically so {@link #record} can run in its own {@code
 * REQUIRES_NEW} transaction - a rejected attempt (RATE_LIMITED/LIMIT_REACHED) must still be
 * durably logged even though the {@code bookSlot} transaction it's part of is about to roll back
 * on the very exception this class's caller throws right after logging it. A private method on
 * the same class would bypass Spring's proxy and silently ignore the propagation setting.
 */
@Service
public class BookingAttemptRecorder {

    private final BookingAttemptLogRepository attemptLogRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final ClinicRepository clinicRepository;

    public BookingAttemptRecorder(
            BookingAttemptLogRepository attemptLogRepository,
            PatientAccountRepository patientAccountRepository,
            ClinicRepository clinicRepository) {
        this.attemptLogRepository = attemptLogRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID patientAccountId, UUID clinicId, BookingAttemptOutcome outcome, Booking booking) {
        PatientAccount patientAccount = patientAccountRepository.getReferenceById(patientAccountId);
        Clinic clinic = clinicRepository.getReferenceById(clinicId);
        attemptLogRepository.save(new BookingAttemptLog(patientAccount, clinic, Instant.now(), outcome, booking));
    }
}
