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
 * BookingProtectionService}) so its transaction settings go through Spring's proxy - a private
 * method on the same class would silently ignore them.
 *
 * <p>{@link #record} is called once the attempt's own transaction has ended ({@code
 * BookingProtectionService.recordAfterRollback}), or with a caller's outer transaction (a
 * waitlist claim) still open and doomed around it - {@code REQUIRES_NEW} keeps the row out of
 * that. {@link #recordAdmitted} is the opposite case: it joins the attempt's own transaction,
 * which holds the patient row lock.
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
        save(patientAccountId, clinicId, outcome, booking);
    }

    /** An admitted attempt, logged as OTHER_FAILURE until its booking succeeds. Returns its id. */
    @Transactional
    public UUID recordAdmitted(UUID patientAccountId, UUID clinicId) {
        return save(patientAccountId, clinicId, BookingAttemptOutcome.OTHER_FAILURE, null).getId();
    }

    private BookingAttemptLog save(UUID patientAccountId, UUID clinicId, BookingAttemptOutcome outcome, Booking booking) {
        PatientAccount patientAccount = patientAccountRepository.getReferenceById(patientAccountId);
        Clinic clinic = clinicRepository.getReferenceById(clinicId);
        return attemptLogRepository.save(new BookingAttemptLog(patientAccount, clinic, Instant.now(), outcome, booking));
    }
}
