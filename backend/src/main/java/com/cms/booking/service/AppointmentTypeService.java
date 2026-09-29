package com.cms.booking.service;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.DoctorDefaultFee;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.DoctorProfileNotFoundException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.DoctorDefaultFeeRepository;


import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 017: create/list Appointment Types and set a Doctor's default fee - the configuration surface {@link FeeResolutionService} reads. */
@Service
public class AppointmentTypeService {

    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final DoctorDefaultFeeRepository doctorDefaultFeeRepository;

    public AppointmentTypeService(
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            DoctorDefaultFeeRepository doctorDefaultFeeRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.doctorDefaultFeeRepository = doctorDefaultFeeRepository;
    }

    @Transactional
    public AppointmentType create(UUID callerAccountId, UUID doctorProfileId, String name, BigDecimal feeOverride) {
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        requireAuthorized(callerAccountId, doctorProfile);
        return appointmentTypeRepository.save(new AppointmentType(doctorProfile, name, feeOverride));
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
        AppointmentType appointmentType = appointmentTypeRepository
                .findById(appointmentTypeId)
                .filter(type -> type.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new AppointmentTypeNotFoundException(appointmentTypeId));
        appointmentType.rename(name, feeOverride);
        return appointmentTypeRepository.save(appointmentType);
    }

    /**
     * FR-007: upsert - creates the Doctor's default fee if absent, otherwise replaces the amount
     * (spec Assumptions: at most one per Doctor).
     *
     * <p>_diagnostics [MAJOR] - [full-repo-audit] - [RACE_500]: the {@code uq_doctor_default_fee_
     * doctor_profile} unique constraint protects the data, but unlike every other race-prone
     * insert in this codebase ({@code PatientBookingService.bookSlot}, {@code
     * PatientLinkingService.findOrCreatePatient}), this upsert used plain {@code save()} with no
     * {@link DataIntegrityViolationException} handling - two concurrent first-time "set default
     * fee" calls for the same doctor would have one succeed and the other raw-500 straight out of
     * an unmapped exception. Now {@code saveAndFlush} forces the constraint check to happen here
     * (not at some later, uncatchable flush), and losing the race re-fetches the row the other
     * caller just inserted and applies this caller's amount to it instead of failing - the
     * request completes with the correct end state either way, matching this method's own
     * "last write wins" upsert semantics.
     */
    @Transactional
    public DoctorDefaultFee setDefaultFee(UUID callerAccountId, UUID doctorProfileId, BigDecimal amount) {
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        requireAuthorized(callerAccountId, doctorProfile);

        var existing = doctorDefaultFeeRepository.findByDoctorProfile_Id(doctorProfileId);
        if (existing.isPresent()) {
            DoctorDefaultFee fee = existing.get();
            fee.setAmount(amount);
            return doctorDefaultFeeRepository.save(fee);
        }

        try {
            return doctorDefaultFeeRepository.saveAndFlush(new DoctorDefaultFee(doctorProfile, amount));
        } catch (DataIntegrityViolationException e) {
            DoctorDefaultFee fee = doctorDefaultFeeRepository.findByDoctorProfile_Id(doctorProfileId).orElseThrow(() -> e);
            fee.setAmount(amount);
            return doctorDefaultFeeRepository.save(fee);
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
