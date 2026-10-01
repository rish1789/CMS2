package com.cms.booking.service;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.ClinicAppointmentTypePrice;
import com.cms.booking.domain.ClinicDoctorFee;
import com.cms.booking.dto.ClinicDoctorFeesResponse;
import com.cms.booking.dto.ClinicDoctorFeesResponse.AppointmentTypeFee;
import com.cms.booking.exception.AppointmentTypeNotFoundException;
import com.cms.booking.exception.DoctorProfileNotFoundException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidFeeAmountException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 068-per-clinic-fees US2 (FR-005/FR-006, contracts/clinic-fees-api.md): a doctor's prices at
 * one clinic. Only an active ClinicAdmin of that clinic sets them, and only for a doctor actively
 * staffed there; any active staff member of the clinic can read them.
 */
@Service
public class ClinicFeeService {

    /** NUMERIC(10,2): at most 8 digits before the decimal point. */
    private static final int MAX_INTEGER_DIGITS = 8;

    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final AppointmentTypeRepository appointmentTypeRepository;
    private final ClinicDoctorFeeRepository clinicDoctorFeeRepository;
    private final ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

    public ClinicFeeService(
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            AppointmentTypeRepository appointmentTypeRepository,
            ClinicDoctorFeeRepository clinicDoctorFeeRepository,
            ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository) {
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.appointmentTypeRepository = appointmentTypeRepository;
        this.clinicDoctorFeeRepository = clinicDoctorFeeRepository;
        this.clinicAppointmentTypePriceRepository = clinicAppointmentTypePriceRepository;
    }

    @Transactional(readOnly = true)
    public ClinicDoctorFeesResponse get(UUID callerAccountId, UUID clinicId, UUID doctorProfileId) {
        // FR-006: this clinic's active staff, or the doctor concerned (their own prices, even after
        // their role at this clinic was deactivated - reading never changes anything). The access
        // check comes before "not found", so an outsider cannot probe which doctor ids exist.
        Optional<DoctorProfile> doctorProfile = doctorProfileRepository.findById(doctorProfileId);
        boolean isDoctorConcerned = doctorProfile
                .map(profile -> profile.getAccount().getId().equals(callerAccountId))
                .orElse(false);
        if (!isDoctorConcerned
                && !roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId)) {
            throw new ForbiddenException("Only staff of this clinic can see its prices");
        }
        doctorProfile.orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
        return view(clinicId, doctorProfileId);
    }

    @Transactional
    public ClinicDoctorFeesResponse setDefault(
            UUID callerAccountId, UUID clinicId, UUID doctorProfileId, BigDecimal amount) {
        requireWriteAccess(callerAccountId, clinicId, doctorProfileId);
        clinicDoctorFeeRepository.upsert(clinicId, doctorProfileId, validated(amount), callerAccountId);
        return view(clinicId, doctorProfileId);
    }

    @Transactional
    public ClinicDoctorFeesResponse setTypePrice(
            UUID callerAccountId, UUID clinicId, UUID doctorProfileId, UUID appointmentTypeId, BigDecimal amount) {
        requireWriteAccess(callerAccountId, clinicId, doctorProfileId);
        requireDoctorsType(doctorProfileId, appointmentTypeId);
        clinicAppointmentTypePriceRepository.upsert(clinicId, appointmentTypeId, validated(amount), callerAccountId);
        return view(clinicId, doctorProfileId);
    }

    @Transactional
    public ClinicDoctorFeesResponse removeTypePrice(
            UUID callerAccountId, UUID clinicId, UUID doctorProfileId, UUID appointmentTypeId) {
        requireWriteAccess(callerAccountId, clinicId, doctorProfileId);
        requireDoctorsType(doctorProfileId, appointmentTypeId);
        clinicAppointmentTypePriceRepository.deleteByClinicAndAppointmentType(clinicId, appointmentTypeId);
        return view(clinicId, doctorProfileId);
    }

    /** FR-005: an active ClinicAdmin of this clinic, for a doctor actively staffed at this clinic. */
    private void requireWriteAccess(UUID callerAccountId, UUID clinicId, UUID doctorProfileId) {
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                callerAccountId, clinicId, RoleAssignment.Role.ClinicAdmin)) {
            throw new ForbiddenException("Only this clinic's admin can change its prices");
        }
        DoctorProfile doctorProfile = loadDoctorProfile(doctorProfileId);
        if (!roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                doctorProfile.getAccount().getId(), clinicId, RoleAssignment.Role.Doctor)) {
            throw new ForbiddenException("This doctor is not staffed at this clinic");
        }
    }

    private void requireDoctorsType(UUID doctorProfileId, UUID appointmentTypeId) {
        appointmentTypeRepository
                .findById(appointmentTypeId)
                .filter(type -> type.getDoctorProfile().getId().equals(doctorProfileId))
                .orElseThrow(() -> new AppointmentTypeNotFoundException(appointmentTypeId));
    }

    private DoctorProfile loadDoctorProfile(UUID doctorProfileId) {
        return doctorProfileRepository
                .findById(doctorProfileId)
                .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
    }

    private static BigDecimal validated(BigDecimal amount) {
        if (amount == null
                || amount.signum() < 0
                || amount.stripTrailingZeros().scale() > 2
                || amount.precision() - amount.scale() > MAX_INTEGER_DIGITS) {
            throw new InvalidFeeAmountException();
        }
        return amount;
    }

    private ClinicDoctorFeesResponse view(UUID clinicId, UUID doctorProfileId) {
        BigDecimal defaultFee = clinicDoctorFeeRepository
                .findByClinic_IdAndDoctorProfile_Id(clinicId, doctorProfileId)
                .map(ClinicDoctorFee::getAmount)
                .orElse(null);
        Map<UUID, BigDecimal> prices = clinicAppointmentTypePriceRepository
                .findByClinic_IdAndAppointmentType_DoctorProfile_Id(clinicId, doctorProfileId)
                .stream()
                .collect(Collectors.toMap(p -> p.getAppointmentType().getId(), ClinicAppointmentTypePrice::getAmount));
        List<AppointmentTypeFee> types = appointmentTypeRepository.findByDoctorProfile_Id(doctorProfileId).stream()
                .sorted(Comparator.comparing(AppointmentType::getName))
                .map(type -> {
                    BigDecimal price = prices.get(type.getId());
                    return new AppointmentTypeFee(type.getId(), type.getName(), price, price != null ? price : defaultFee);
                })
                .toList();
        return new ClinicDoctorFeesResponse(clinicId, doctorProfileId, defaultFee, types);
    }
}
