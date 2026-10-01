package com.cms.booking.service;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.DoctorProfileNotFoundException;
import com.cms.booking.exception.FeeMovedToClinicException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.repository.AppointmentTypeRepository;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 017: create/list/rename a Doctor's Appointment Types. 068-per-clinic-fees: prices are no longer
 * set here - see {@link ClinicFeeService}; {@link FeeResolutionService} reads the clinic prices.
 */
@Service
public class AppointmentTypeService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;

    public AppointmentTypeService(
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AppointmentTypeRepository appointmentTypeRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
    }

    @Transactional
    public AppointmentType create(UUID callerAccountId, UUID doctorProfileId, String name, BigDecimal feeOverride) {
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        requireAuthorized(callerAccountId, doctorProfile);
        rejectDoctorWideFee(feeOverride);
        return appointmentTypeRepository.save(new AppointmentType(doctorProfile, name, null));
    }

    @Transactional(readOnly = true)
    public List<AppointmentType> list(UUID callerAccountId, UUID doctorProfileId) {
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        requireAuthorized(callerAccountId, doctorProfile);
        return appointmentTypeRepository.findByDoctorProfile_Id(doctorProfileId);
    }

    /**
     * 053: real-bug-fix - create/list had no way to correct a mistyped name or fee override after
     * the fact. A rename only ever changes this row's own columns, so it's always safe even once
     * a Booking already references this AppointmentType (unlike a delete, which this method
     * deliberately does not add - no confirmed requirement for it yet, and it would need a
     * DELETION_BLOCKED-style guard against exactly that reference, mirroring the doctor/clinic
     * permanent-delete gate).
     */
    @Transactional
    public AppointmentType rename(
            UUID callerAccountId, UUID doctorProfileId, UUID appointmentTypeId, String name, BigDecimal feeOverride) {
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        requireAuthorized(callerAccountId, doctorProfile);
        rejectDoctorWideFee(feeOverride);
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(appointmentTypeId)
                .filter(type -> type.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new AppointmentTypeNotFoundException(appointmentTypeId));
        appointmentType.rename(name, appointmentType.getFeeOverride());
        return appointmentTypeRepository.save(appointmentType);
    }

    /** 068-per-clinic-fees FR-012: a type carries no price of its own - prices are set per clinic. */
    private static void rejectDoctorWideFee(BigDecimal feeOverride) {
        if (feeOverride != null) {
            throw FeeMovedToClinicException.feeNotAccepted();
        }
    }

    private DoctorProfile loadDoctorProfile(UUID doctorProfileId) {
        return doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
    }

    /** FR-005/FR-006: the Doctor themselves, or an active ClinicAdmin at any clinic that Doctor is actively staffed at. */
    private void requireAuthorized(UUID callerAccountId, DoctorProfile doctorProfile) {
        if (doctorProfile.getAccount().getId().equals(callerAccountId)) {
            return;
        }

        UUID doctorAccountId = doctorProfile.getAccount().getId();
        boolean isClinicAdminAtAnyOfDoctorsClinics = roleAssignmentRepository
                .findByAccount_IdAndRoleAndActiveTrue(doctorAccountId, RoleAssignment.Role.Doctor)
                .stream()
                .anyMatch(ra -> roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerAccountId, ra.getClinic().getId(), RoleAssignment.Role.ClinicAdmin));

        if (!isClinicAdminAtAnyOfDoctorsClinics) {
            throw new ForbiddenException();
        }
    }
}
