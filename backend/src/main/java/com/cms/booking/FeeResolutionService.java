package com.cms.booking;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 017: the feature's core exported contract - see contracts/fee-resolution.md. No HTTP
 * endpoint (Constitution Principle III's service-interface allowance): 016/017/018 (not
 * yet built) are the only eventual callers.
 */
@Service
public class FeeResolutionService {

    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    public FeeResolutionService(
            AppointmentTypeRepository appointmentTypeRepository, DoctorDefaultFeeRepository doctorDefaultFeeRepository) {
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorDefaultFeeRepository = doctorDefaultFeeRepository;
    }

    /** FR-001..FR-004: override, else default fee, else a hard block - never a fabricated or zero amount. */
    @Transactional(readOnly = true)
    public BigDecimal resolve(UUID doctorProfileId, UUID appointmentTypeId) {
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(appointmentTypeId)
                .filter(at -> at.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new AppointmentTypeNotFoundException(appointmentTypeId));

        if (appointmentType.getFeeOverride() != null) {
            return appointmentType.getFeeOverride();
        }

        return doctorDefaultFeeRepository
                .findByDoctorProfile_Id(doctorProfileId)
                .map(DoctorDefaultFee::getAmount)
                .orElseThrow(() -> new NoFeeConfiguredException(doctorProfileId, appointmentTypeId));
    }
}
