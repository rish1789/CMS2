package com.cms.booking;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppointmentTypeRepository extends JpaRepository<AppointmentType, UUID> {

    List<AppointmentType> findByDoctorProfile_Id(UUID doctorProfileId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a defined appointment type counts as real activity. */
    long countByDoctorProfile_Id(UUID doctorProfileId);
}
