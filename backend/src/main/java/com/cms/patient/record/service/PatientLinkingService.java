package com.cms.patient.record.service;

import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.exception.PatientAccountNotFoundException;
import com.cms.patient.record.repository.PatientRepository;


import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements 009's FR-001..FR-004: the sole entry point 017/018 will call on a patient's
 * first booking at a clinic. No HTTP endpoint exists (plan.md) - this service method is
 * the whole public contract (contracts/patient-linking-service.md).
 */
@Service
public class PatientLinkingService {

    private static final Logger log = LoggerFactory.getLogger(PatientLinkingService.class);

    private final PatientAccountRepository patientAccountRepository;
    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;

    public PatientLinkingService(
            PatientAccountRepository patientAccountRepository,
            PatientRepository patientRepository,
            ClinicRepository clinicRepository) {
        this.patientAccountRepository = patientAccountRepository;
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional
    public Patient findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name) {
        // 066-patient-linking-race (research.md R3): a row lock on the Patient Account, held until
        // the caller's transaction ends, serializes concurrent calls for the same account - the
        // same lock 060's BookingProtectionService.checkBookingLimit takes (a no-op re-lock when
        // it already did, in the same transaction). A second caller waits here, then sees the
        // first caller's committed record via the FR-002 lookup below, or creates its own if the
        // first rolled back - so it never issues a conflicting INSERT (009 FR-006).
        PatientAccount account = patientAccountRepository
                .findWithLockById(patientAccountId)
                .orElseThrow(() -> new PatientAccountNotFoundException(patientAccountId));

        // FR-002: already linked at this clinic - reuse directly, no phone re-matching.
        var existingLink = patientRepository.findByClinic_IdAndPatientAccount_Id(clinicId, patientAccountId);
        if (existingLink.isPresent()) {
            log.info("Patient linking: reused existing link, accountId={}, clinicId={}", patientAccountId, clinicId);
            return existingLink.get();
        }

        // FR-003: an unlinked walk-in record with a matching phone - link it. Excludes
        // records already linked to a DIFFERENT account (the repository query only
        // matches patientAccount IS NULL rows), protecting that account's data
        // (Clarifications).
        String mobile = account.getMobile();
        if (mobile != null && !mobile.isBlank()) {
            var phoneMatch = patientRepository.findByClinic_IdAndPhoneAndPatientAccountIsNull(clinicId, mobile);
            if (phoneMatch.isPresent()) {
                Patient matched = phoneMatch.get();
                matched.setPatientAccount(account);
                patientRepository.save(matched);
                log.info("Patient linking: matched and linked walk-in, accountId={}, clinicId={}", patientAccountId, clinicId);
                return matched;
            }
        }

        // FR-004: no existing link, no match (or no phone to match on) - create new.
        Clinic clinic = clinicRepository
                .findById(clinicId)
                .orElseThrow(() -> new NoSuchElementException("No clinic with id " + clinicId));
        // No catch for uq_patient_clinic_account here (066 research.md R1/R3): the account lock
        // above means a same-account race never reaches this INSERT, and a re-read after a failed
        // INSERT could never work anyway - PostgreSQL aborts the whole transaction. The index stays
        // as the data-layer backstop (FR-005a); a violation from any other writer propagates.
        // saveAndFlush (not save) keeps the INSERT - and its constraint check - inside this method.
        Patient created = patientRepository.saveAndFlush(new Patient(clinic, account, name, mobile));
        log.info("Patient linking: created new record, accountId={}, clinicId={}", patientAccountId, clinicId);
        return created;
    }
}
