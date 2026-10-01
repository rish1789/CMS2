package com.cms.booking.repository;

import com.cms.booking.domain.DoctorDefaultFee;


import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 068-per-clinic-fees: the retired doctor-wide default fee. Its rows are kept as the pre-068 audit
 * trail and are never read for prices (FR-012); only the doctor delete gate and test cleanup use it.
 */
public interface DoctorDefaultFeeRepository extends JpaRepository<DoctorDefaultFee, UUID> {

    /** super-admin-console-redesign: the doctor permanent-delete gate - a set default fee counts as real activity. */
    boolean existsByDoctorProfile_Id(UUID doctorProfileId);
}
