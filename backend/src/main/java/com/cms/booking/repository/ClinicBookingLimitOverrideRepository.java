package com.cms.booking.repository;

import com.cms.booking.domain.ClinicBookingLimitOverride;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicBookingLimitOverrideRepository extends JpaRepository<ClinicBookingLimitOverride, UUID> {

    Optional<ClinicBookingLimitOverride> findByClinic_Id(UUID clinicId);

    void deleteByClinic_Id(UUID clinicId);
}
