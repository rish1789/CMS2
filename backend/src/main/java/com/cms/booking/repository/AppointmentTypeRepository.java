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
}
