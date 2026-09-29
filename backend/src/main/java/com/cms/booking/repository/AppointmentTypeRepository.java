package com.cms.booking.repository;

import com.cms.booking.domain.AppointmentType;


import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, UUID> {

    List<AppointmentType> findByDoctorProfile_Id(UUID doctorProfileId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a defined appointment type counts as real activity. */
    long countByDoctorProfile_Id(UUID doctorProfileId);

    /**
     * real-bug-fix 2026-09-17: the "booking setup incomplete" warning on the staff-console
     * Doctors page - one bulk membership query for every doctor on the current page, instead of
     * a countByDoctorProfile_Id call per row.
     */
    @Query("SELECT DISTINCT a.doctorProfile.id FROM AppointmentType a WHERE a.doctorProfile.id IN :doctorProfileIds")
    List<UUID> findDoctorProfileIdsWithAppointmentTypes(@Param("doctorProfileIds") Collection<UUID> doctorProfileIds);

    /**
     * real-bug-fix 2026-09-17: found live testing the warning above - flagging every doctor with
     * no default fee set (including one whose every AppointmentType already carries its own
     * feeOverride, like Gauresh Kumar) was a false positive: FeeResolutionService never needs
     * the default fee for a type that already has an override, so that doctor is fully bookable
     * despite having no default fee. This narrows "not bookable" to only a doctor with at least
     * one AppointmentType that has neither its own override NOR a default fee to fall back on.
     */
    @Query("SELECT DISTINCT a.doctorProfile.id FROM AppointmentType a "
            + "WHERE a.doctorProfile.id IN :doctorProfileIds AND a.feeOverride IS NULL")
    List<UUID> findDoctorProfileIdsWithAnAppointmentTypeMissingFeeOverride(
            @Param("doctorProfileIds") Collection<UUID> doctorProfileIds);
}
