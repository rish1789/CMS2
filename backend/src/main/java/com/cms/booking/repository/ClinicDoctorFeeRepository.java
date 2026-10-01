package com.cms.booking.repository;

import com.cms.booking.domain.ClinicDoctorFee;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 068-per-clinic-fees: a doctor's default fee per clinic (FR-001). */
public interface ClinicDoctorFeeRepository extends JpaRepository<ClinicDoctorFee, UUID> {

    Optional<ClinicDoctorFee> findByClinic_IdAndDoctorProfile_Id(UUID clinicId, UUID doctorProfileId);

    /**
     * FR-001 upsert, atomic at the database: two concurrent first-time sets for the same
     * (clinic, doctor) both succeed and the last write wins - no unique-constraint 500.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "INSERT INTO clinic_doctor_fee (clinic_id, doctor_profile_id, amount, updated_at, updated_by_account_id) "
                    + "VALUES (:clinicId, :doctorProfileId, :amount, now(), :updatedBy) "
                    + "ON CONFLICT (clinic_id, doctor_profile_id) DO UPDATE SET amount = EXCLUDED.amount, "
                    + "updated_at = EXCLUDED.updated_at, updated_by_account_id = EXCLUDED.updated_by_account_id",
            nativeQuery = true)
    void upsert(
            @Param("clinicId") UUID clinicId,
            @Param("doctorProfileId") UUID doctorProfileId,
            @Param("amount") BigDecimal amount,
            @Param("updatedBy") UUID updatedByAccountId);

    /** FR-010: the per-clinic "booking setup incomplete" check, in bulk for a clinic's doctors. */
    @Query("SELECT f.doctorProfile.id FROM ClinicDoctorFee f "
            + "WHERE f.clinic.id = :clinicId AND f.doctorProfile.id IN :doctorProfileIds")
    List<UUID> findDoctorProfileIdsWithDefaultFeeAtClinic(
            @Param("clinicId") UUID clinicId, @Param("doctorProfileIds") Collection<UUID> doctorProfileIds);

    /** The doctor permanent-delete gate: a clinic price for the doctor counts as real setup. */
    boolean existsByDoctorProfile_Id(UUID doctorProfileId);

    /** The clinic permanent delete removes the clinic's own price configuration. */
    void deleteByClinic_Id(UUID clinicId);
}
