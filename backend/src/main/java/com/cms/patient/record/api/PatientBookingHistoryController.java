package com.cms.patient.record.api;

import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.dto.PatientBookingHistoryResponse;
import com.cms.patient.record.exception.NotStaffedAtClinicException;
import com.cms.patient.record.exception.PatientNotFoundException;
import com.cms.patient.record.repository.PatientRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 052-patient-clinical-hub T005 (contracts/patient-booking-history.md): a clinic-scoped
 * patient's booking history, for the staff-side Patient Hub. Reuses PatientDetailController's
 * exact clinic-ownership check (research.md Decision 2) - no new authorization mechanism.
 *
 * <p>doctor-console-cross-doctor-leak fix: also reuses ClinicSessionListController's doctor
 * self-scoping - a caller whose only active role at this clinic is Doctor sees only this
 * patient's encounters with them here, not the patient's full history with every doctor at the
 * clinic (this tab is one click from a Doctor's home console via Find a Patient). A caller who
 * also holds ClinicAdmin or Operations at this clinic keeps seeing the patient's full history,
 * unchanged.
 */
@RestController
public class PatientBookingHistoryController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BookingRepository bookingRepository;
    private final PatientRepository patientRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DoctorProfileRepository doctorProfileRepository;

    public PatientBookingHistoryController(
            BookingRepository bookingRepository,
            PatientRepository patientRepository,
            RoleAssignmentRepository roleAssignmentRepository,
            DoctorProfileRepository doctorProfileRepository) {
        this.bookingRepository = bookingRepository;
        this.patientRepository = patientRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.doctorProfileRepository = doctorProfileRepository;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/patients/{patientId}/bookings")
    public PatientBookingHistoryResponse list(
            @PathVariable UUID clinicId,
            @PathVariable UUID patientId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        List<RoleAssignment> roles =
                roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId);
        if (roles.isEmpty()) {
            throw new NotStaffedAtClinicException();
        }
        boolean doctorOnly = roles.stream().noneMatch(ra -> ra.getRole() != RoleAssignment.Role.Doctor);

        Patient patient = patientRepository
                .findById(patientId)
                .filter(p -> p.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new PatientNotFoundException(patientId));

        UUID selfScopeDoctorProfileId = null;
        if (doctorOnly) {
            selfScopeDoctorProfileId = doctorProfileRepository
                    .findByAccount_Id(callerAccountId)
                    .map(dp -> dp.getId())
                    .orElse(null);
            if (selfScopeDoctorProfileId == null) {
                // Doctor-only role with no resolvable DoctorProfile - fail closed (empty
                // history) rather than accidentally dropping the self-scope restriction.
                return PatientBookingHistoryResponse.from(
                        org.springframework.data.domain.Page.empty(PageRequest.of(page, size)));
            }
        }

        return PatientBookingHistoryResponse.from(bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(
                patient.getId(), selfScopeDoctorProfileId, PageRequest.of(page, size)));
    }
}
