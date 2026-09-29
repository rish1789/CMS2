package com.cms.booking.repository;

import com.cms.booking.domain.DoctorDefaultFee;


import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorDefaultFeeRepository extends JpaRepository<DoctorDefaultFee, UUID> {

    Optional<DoctorDefaultFee> findByDoctorProfile_Id(UUID doctorProfileId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a set default fee counts as real activity. */
    boolean existsByDoctorProfile_Id(UUID doctorProfileId);

    /** real-bug-fix 2026-09-17: the "booking setup incomplete" warning on the staff-console Doctors page - mirrors AppointmentTypeRepository's identical bulk-membership query. */
    @Query("SELECT f.doctorProfile.id FROM DoctorDefaultFee f WHERE f.doctorProfile.id IN :doctorProfileIds")
    List<UUID> findDoctorProfileIdsWithDefaultFee(@Param("doctorProfileIds") Collection<UUID> doctorProfileIds);
}
