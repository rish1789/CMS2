package com.cms.patient.record;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
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
        PatientAccount account = patientAccountRepository
                .findById(patientAccountId)
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
        try {
            // 021-patient-self-service-booking convergence fix: saveAndFlush (not save)
            // forces the INSERT - and its uq_patient_clinic_account constraint check - to
            // happen synchronously right here, where this catch block can actually
            // intercept it. A plain save() defers the INSERT to whatever later flush
            // happens to occur first (e.g. a caller's own saveAndFlush on an unrelated
            // entity), by which point this method has already returned and no catch here
            // could ever fire - the exact bug class 020 found and fixed in StaffBookingService.
            Patient created = patientRepository.saveAndFlush(new Patient(clinic, account, name, mobile));
            log.info("Patient linking: created new record, accountId={}, clinicId={}", patientAccountId, clinicId);
            return created;
        } catch (DataAccessException e) {
            if (!isUniqueConstraintViolation(e, "uq_patient_clinic_account")) {
                throw e;
            }
            // FR-006: lost the same-account race (uq_patient_clinic_account) against a
            // concurrent call for this same account+clinic - not a failure, the
            // concurrent winner's row is what we return.
            log.info(
                    "Patient linking: lost create race, re-reading winner's record, accountId={}, clinicId={}",
                    patientAccountId,
                    clinicId);
            return patientRepository
                    .findByClinic_IdAndPatientAccount_Id(clinicId, patientAccountId)
                    .orElseThrow(() -> e);
        }
    }

    private boolean isUniqueConstraintViolation(DataAccessException e, String constraintName) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.contains(constraintName);
    }
}
