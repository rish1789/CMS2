package com.cms.booking.repository;

import com.cms.booking.domain.ClinicBookingLimitOverrideChangeLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicBookingLimitOverrideChangeLogRepository
        extends JpaRepository<ClinicBookingLimitOverrideChangeLog, UUID> {

    List<ClinicBookingLimitOverrideChangeLog> findByClinic_IdOrderByChangedAtDesc(UUID clinicId);

    /** Clears a rejected clinic's override history when the clinic itself is permanently deleted. */
    void deleteByClinic_Id(UUID clinicId);
}
