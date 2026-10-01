package com.cms.booking.repository;

import com.cms.booking.domain.ClinicAppointmentTypePrice;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 068-per-clinic-fees: an appointment type's price per clinic (FR-002). */
public interface ClinicAppointmentTypePriceRepository extends JpaRepository<ClinicAppointmentTypePrice, UUID> {

    Optional<ClinicAppointmentTypePrice> findByClinic_IdAndAppointmentType_Id(UUID clinicId, UUID appointmentTypeId);

    /** All of one doctor's type prices at one clinic - for the price screen and effective fees. */
    List<ClinicAppointmentTypePrice> findByClinic_IdAndAppointmentType_DoctorProfile_Id(
            UUID clinicId, UUID doctorProfileId);

    /** FR-002 upsert, atomic at the database - see ClinicDoctorFeeRepository#upsert. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value = "INSERT INTO clinic_appointment_type_price "
                    + "(clinic_id, appointment_type_id, amount, updated_at, updated_by_account_id) "
                    + "VALUES (:clinicId, :appointmentTypeId, :amount, now(), :updatedBy) "
                    + "ON CONFLICT (clinic_id, appointment_type_id) DO UPDATE SET amount = EXCLUDED.amount, "
                    + "updated_at = EXCLUDED.updated_at, updated_by_account_id = EXCLUDED.updated_by_account_id",
            nativeQuery = true)
    void upsert(
            @Param("clinicId") UUID clinicId,
            @Param("appointmentTypeId") UUID appointmentTypeId,
            @Param("amount") BigDecimal amount,
            @Param("updatedBy") UUID updatedByAccountId);

    /** Removing a type's price at a clinic - the clinic default applies again. Idempotent. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM ClinicAppointmentTypePrice p WHERE p.clinic.id = :clinicId AND p.appointmentType.id = :appointmentTypeId")
    void deleteByClinicAndAppointmentType(
            @Param("clinicId") UUID clinicId, @Param("appointmentTypeId") UUID appointmentTypeId);

    /**
     * FR-010: doctors (among the given ones) with at least one appointment type that has no price
     * at this clinic - the per-clinic replacement for the retired doctor-wide "type missing its fee
     * override" readiness query.
     */
    @Query("SELECT DISTINCT t.doctorProfile.id FROM AppointmentType t "
            + "WHERE t.doctorProfile.id IN :doctorProfileIds AND NOT EXISTS ("
            + "SELECT 1 FROM ClinicAppointmentTypePrice p WHERE p.appointmentType = t AND p.clinic.id = :clinicId)")
    List<UUID> findDoctorProfileIdsWithAnAppointmentTypeUnpricedAtClinic(
            @Param("clinicId") UUID clinicId, @Param("doctorProfileIds") Collection<UUID> doctorProfileIds);

    /** The doctor permanent-delete gate: a clinic price on any of the doctor's types counts as real setup. */
    boolean existsByAppointmentType_DoctorProfile_Id(UUID doctorProfileId);

    /** The clinic permanent delete removes the clinic's own price configuration. */
    void deleteByClinic_Id(UUID clinicId);
}
