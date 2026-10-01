package com.cms.booking.service;

import com.cms.booking.dto.DoctorBookingReadinessResponse;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * real-bug-fix 2026-09-17: see DoctorBookingReadinessResponse's own Javadoc for the human-error
 * this surfaces. Every doctor staffed at the clinic gets one row - a doctor with zero
 * AppointmentTypes and no default fee genuinely cannot be booked by a patient yet, and staff had
 * no way to see that without clicking into that one doctor's own Appointment Types page.
 */
@Service
public class DoctorBookingReadinessService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final ClinicDoctorFeeRepository clinicDoctorFeeRepository;
    private final ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    public DoctorBookingReadinessService(
            DoctorProfileRepository doctorProfileRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            ClinicDoctorFeeRepository clinicDoctorFeeRepository,
            ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.clinicDoctorFeeRepository = clinicDoctorFeeRepository;
        this.clinicAppointmentTypePriceRepository = clinicAppointmentTypePriceRepository;
    }

    @Transactional(readOnly = true)
    public List<DoctorBookingReadinessResponse> forClinic(UUID clinicId) {
        List<DoctorProfile> doctors =
                doctorProfileRepository.findByClinicStaffed(clinicId, null, Pageable.unpaged()).getContent();
        List<UUID> doctorProfileIds = doctors.stream().map(DoctorProfile::getId).toList();
        if (doctorProfileIds.isEmpty()) {
            return List.of();
        }

        Set<UUID> withAppointmentTypes =
                new HashSet<>(appointmentTypeRepository.findDoctorProfileIdsWithAppointmentTypes(doctorProfileIds));
        // 068-per-clinic-fees FR-010: fees are this clinic's own - a doctor priced only elsewhere is not ready here.
        Set<UUID> withDefaultFee = new HashSet<>(
                clinicDoctorFeeRepository.findDoctorProfileIdsWithDefaultFeeAtClinic(clinicId, doctorProfileIds));
        Set<UUID> withMissingOverride = new HashSet<>(clinicAppointmentTypePriceRepository
                .findDoctorProfileIdsWithAnAppointmentTypeUnpricedAtClinic(clinicId, doctorProfileIds));

        return doctorProfileIds.stream()
                .map(id -> new DoctorBookingReadinessResponse(
                        id, withAppointmentTypes.contains(id), withDefaultFee.contains(id), withMissingOverride.contains(id)))
                .toList();
    }
}
