package com.cms.scheduling;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findByClinic_IdAndDoctorProfile_Id(UUID clinicId, UUID doctorProfileId);

    /** 014: every Schedule for this doctor, across every clinic - the overlap-check's candidate set. */
    List<Schedule> findByDoctorProfile_Id(UUID doctorProfileId);

    /** super-admin-console-redesign: the clinic permanent-delete gate - a defined Schedule counts as real activity. */
    long countByClinic_Id(UUID clinicId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a defined Schedule counts as real activity. */
    long countByDoctorProfile_Id(UUID doctorProfileId);
}
