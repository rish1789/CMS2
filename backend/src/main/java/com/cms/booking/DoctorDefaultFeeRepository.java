package com.cms.booking;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorDefaultFeeRepository extends JpaRepository<DoctorDefaultFee, UUID> {

    Optional<DoctorDefaultFee> findByDoctorProfile_Id(UUID doctorProfileId);

    /** super-admin-console-redesign: the doctor permanent-delete gate - a set default fee counts as real activity. */
    boolean existsByDoctorProfile_Id(UUID doctorProfileId);
}
