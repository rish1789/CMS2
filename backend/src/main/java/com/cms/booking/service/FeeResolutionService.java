package com.cms.booking.service;

import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.NoFeeConfiguredException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 017: the feature's core exported contract - see contracts/fee-resolution.md. No HTTP
 * endpoint (Constitution Principle III's service-interface allowance): every booking path
 * (patient/staff fixed-time and queue, front-desk walk-in, waitlist claim) calls it.
 *
 * <p>068-per-clinic-fees (SEC-03): prices are resolved from the booking's own clinic only -
 * that clinic's type price, else that clinic's default fee for the doctor, else a hard block.
 * Never another clinic's price and never the retired doctor-wide fields (FR-003/FR-004/FR-012).
 */
@Service
public class FeeResolutionService {

    private final AppointmentTypeRepository appointmentTypeRepository;
    private final ClinicDoctorFeeRepository clinicDoctorFeeRepository;
    private final ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    public FeeResolutionService(
            AppointmentTypeRepository appointmentTypeRepository,
            ClinicDoctorFeeRepository clinicDoctorFeeRepository,
            ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository) {
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.clinicDoctorFeeRepository = clinicDoctorFeeRepository;
        this.clinicAppointmentTypePriceRepository = clinicAppointmentTypePriceRepository;
    }

    /** Type price at the clinic, else the clinic's default fee, else a hard block - never a fabricated or zero amount. */
    @Transactional(readOnly = true)
    public BigDecimal resolve(UUID clinicId, UUID doctorProfileId, UUID appointmentTypeId) {
        appointmentTypeRepository
                .findById(appointmentTypeId)
                .filter(at -> at.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new AppointmentTypeNotFoundException(appointmentTypeId));

        return clinicAppointmentTypePriceRepository
                .findByClinic_IdAndAppointmentType_Id(clinicId, appointmentTypeId)
                .map(ClinicAppointmentTypePrice::getAmount)
                .or(() -> clinicDoctorFeeRepository
                        .findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId)
                        .map(ClinicDoctorFee::getAmount))
                .orElseThrow(() -> new NoFeeConfiguredException(doctorProfileId, appointmentTypeId));
    }

    /**
     * 068 FR-011: each of the doctor's appointment types mapped to its effective fee at this
     * clinic, by the same precedence as {@link #resolve}. A type with no fee here is absent.
     */
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> effectiveFees(UUID clinicId, UUID doctorProfileId) {
        BigDecimal defaultFee = clinicDoctorFeeRepository
                .findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId)
                .map(ClinicDoctorFee::getAmount)
                .orElse(null);
        Map<UUID, BigDecimal> fees = new HashMap<>();
        if (defaultFee != null) {
            appointmentTypeRepository.findByDoctorProfile_Id(doctorProfileId).forEach(type -> fees.put(type.getId(), defaultFee));
        }
        clinicAppointmentTypePriceRepository
                .findByClinic_IdAndAppointmentType_DoctorProfile_Id(clinicId, doctorProfileId)
                .forEach(price -> fees.put(price.getAppointmentType().getId(), price.getAmount()));
        return fees;
    }
}
