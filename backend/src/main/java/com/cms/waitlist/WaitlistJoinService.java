package com.cms.waitlist;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.notification.PatientAccountNotFoundException;
import com.cms.patient.account.PatientAccount;
import com.cms.patient.account.PatientAccountRepository;
import com.cms.scheduling.ClinicNotFoundException;
import com.cms.scheduling.DoctorNotStaffedAtClinicException;
import com.cms.scheduling.DoctorProfileNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 031: the shared core both {@link PatientWaitlistController} and
 * {@link StaffWaitlistController} call - no authorization here, that's each caller-side
 * controller's own job (mirrors 028's {@code BookingCancellationService} split).
 */
@Service
public class WaitlistJoinService {

    private final ClinicRepository clinicRepository;
    private final DoctorProfileRepository doctorProfileRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final PatientAccountRepository patientAccountRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;

    public WaitlistJoinService(
            ClinicRepository clinicRepository,
            DoctorProfileRepository doctorProfileRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            PatientAccountRepository patientAccountRepository,
            WaitlistEntryRepository waitlistEntryRepository) {
        this.clinicRepository = clinicRepository;
        this.doctorProfileRepository = doctorProfileRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.patientAccountRepository = patientAccountRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
    }

    /** FR-001..FR-003, research.md R6. */
    @Transactional
    public WaitlistEntry join(UUID clinicId, UUID patientAccountId, UUID doctorProfileId, String specialization) {
        Clinic clinic = clinicRepository.findById(clinicId).orElseThrow(() -> new ClinicNotFoundException(clinicId));
        PatientAccount patientAccount = patientAccountRepository
                .findById(patientAccountId)
                .orElseThrow(() -> new PatientAccountNotFoundException(patientAccountId));

        DoctorProfile doctorProfile = null;
        if (doctorProfileId != null) {
            doctorProfile = doctorProfileRepository
                    .findById(doctorProfileId)
                    .orElseThrow(() -> new DoctorProfileNotFoundException(doctorProfileId));
            boolean staffed = roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                    doctorProfile.getAccount().getId(), clinicId, RoleAssignment.Role.Doctor);
            if (!staffed) {
                throw new DoctorNotStaffedAtClinicException(doctorProfileId, clinicId);
            }
        }

        WaitlistEntry entry = new WaitlistEntry(clinic, patientAccount, doctorProfile, specialization);
        return waitlistEntryRepository.save(entry);
    }
}
