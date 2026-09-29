package com.cms.booking.service;

import com.cms.booking.dto.DoctorBookingReadinessResponse;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.DoctorDefaultFeeRepository;
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
    private final DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    public DoctorBookingReadinessService(
            DoctorProfileRepository doctorProfileRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            DoctorDefaultFeeRepository doctorDefaultFeeRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorDefaultFeeRepository = doctorDefaultFeeRepository;
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
        Set<UUID> withDefaultFee =
                new HashSet<>(doctorDefaultFeeRepository.findDoctorProfileIdsWithDefaultFee(doctorProfileIds));
        Set<UUID> withMissingOverride = new HashSet<>(
                appointmentTypeRepository.findDoctorProfileIdsWithAnAppointmentTypeMissingFeeOverride(doctorProfileIds));

        return doctorProfileIds.stream()
                .map(id -> new DoctorBookingReadinessResponse(
                        id, withAppointmentTypes.contains(id), withDefaultFee.contains(id), withMissingOverride.contains(id)))
                .toList();
    }
}
