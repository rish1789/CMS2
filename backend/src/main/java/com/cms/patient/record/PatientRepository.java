package com.cms.patient.record;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    /** 009 FR-002: the existing-link check, tried first. */
    Optional<Patient> findByClinic_IdAndPatientAccount_Id(UUID clinicId, UUID patientAccountId);

    /** 009 FR-003: matches only an unlinked record - implements the linked-record protection (Clarifications). */
    Optional<Patient> findByClinic_IdAndPhoneAndPatientAccountIsNull(UUID clinicId, String phone);

    /**
     * 041-staff-console-pickers FR-007/research.md R4: clinic-scoped, case-insensitive partial
     * name OR phone match. Excludes already-anonymized patients (037) - {@code
     * Patient.anonymize()} sets {@code name} to the fixed literal "Anonymized Patient", so
     * without this filter every anonymized record at a clinic would surface identically and
     * uselessly on a broad search (Analyze-stage F1 fix).
     *
     * <p>pagination-unification-2026-09-10: paginated - a large clinic's patient panel (search
     * hits, not just the raw table) can exceed hundreds of rows for a common name/phone prefix.
     */
    @Query("SELECT p FROM Patient p WHERE p.clinic.id = :clinicId AND p.anonymizedAt IS NULL AND "
            + "(LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')) OR p.phone LIKE CONCAT('%', :term, '%'))")
    Page<Patient> search(@Param("clinicId") UUID clinicId, @Param("term") String term, Pageable pageable);

    /** super-admin-console-redesign: the clinic permanent-delete gate - real patient records are never auto-cascaded away. */
    long countByClinic_Id(UUID clinicId);

    /**
     * patient-booking-flow-rebuild: "My clinics" - every clinic this Patient Account has a
     * linked record at, replacing the "type a Clinic ID" entry point (StaffClinicController's
     * own {@code findByAccount_IdAndActiveTrue} is the equivalent staff-side precedent).
     * Excludes anonymized records for the same reason {@link #search} does.
     */
    Page<Patient> findByPatientAccount_IdAndAnonymizedAtIsNullOrderByCreatedAtDesc(
            UUID patientAccountId, Pageable pageable);
}
