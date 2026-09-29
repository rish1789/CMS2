package com.cms.patient.record.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.api.PatientBookingHistoryController;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.exception.NotStaffedAtClinicException;
import com.cms.patient.record.exception.PatientNotFoundException;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * 052-patient-clinical-hub T001: pure Mockito, no Spring context - mirrors
 * PatientDetailController's own clinic-ownership check (research.md Decision 2), reused
 * verbatim rather than a new authorization mechanism (FR-003).
 *
 * <p>doctor-console-cross-doctor-leak fix: also covers the doctor self-scoping now applied
 * here, mirroring TodayPatientsControllerTest's own coverage of the same pattern.
 */
@ExtendWith(MockitoExtension.class)
class PatientBookingHistoryControllerTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private DoctorProfileRepository doctorProfileRepository;

    @Mock
    private Patient patient;

    @Mock
    private Booking booking;

    @Mock
    private Slot slot;

    @Mock
    private Session session;

    @Mock
    private DoctorProfile doctorProfile;

    @Mock
    private Account doctorAccount;

    @Mock
    private AppointmentType appointmentType;

    private PatientBookingHistoryController newController() {
        return new PatientBookingHistoryController(
                bookingRepository, patientRepository, roleAssignmentRepository, doctorProfileRepository);
    }

    private static UsernamePasswordAuthenticationToken authenticationFor(UUID accountId) {
        return new UsernamePasswordAuthenticationToken(accountId, null);
    }

    private static RoleAssignment mockRole(RoleAssignment.Role role) {
        RoleAssignment roleAssignment = org.mockito.Mockito.mock(RoleAssignment.class);
        when(roleAssignment.getRole()).thenReturn(role);
        return roleAssignment;
    }

    @Test
    void returnsTheBookingPageForAPatientConfirmedAtThisClinic() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var clinic = mockClinicWithId(clinicId);
        RoleAssignment clinicAdminRole = mockRole(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        when(patient.getClinic()).thenReturn(clinic);
        when(patient.getId()).thenReturn(patientId);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(booking.getSlot()).thenReturn(slot);
        when(slot.getSession()).thenReturn(session);
        when(slot.getStartTime()).thenReturn(LocalTime.of(9, 0));
        when(slot.getStatus()).thenReturn(SlotStatus.BOOKED);
        when(session.getSessionDate()).thenReturn(LocalDate.of(2026, 9, 15));
        when(session.getDoctorProfile()).thenReturn(doctorProfile);
        when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        when(doctorAccount.getName()).thenReturn("Dr. Test");
        when(booking.getAppointmentType()).thenReturn(appointmentType);
        when(appointmentType.getName()).thenReturn("Consultation");
        when(booking.getStatus()).thenReturn(BookingStatus.ACTIVE);
        Page<Booking> page = new PageImpl<>(List.of(booking), PageRequest.of(0, 20), 1);
        when(bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(eq(patientId), isNull(), any()))
                .thenReturn(page);

        var response = newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId));

        assertThat(response.totalCount()).isEqualTo(1);
        assertThat(response.bookings()).hasSize(1);
    }

    @Test
    void throwsPatientNotFoundWhenThePatientBelongsToADifferentClinic() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID otherClinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var otherClinic = mockClinicWithId(otherClinicId);
        RoleAssignment clinicAdminRole = mockRole(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        when(patient.getClinic()).thenReturn(otherClinic);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId)))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void throwsPatientNotFoundWhenNoSuchPatientExists() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        RoleAssignment clinicAdminRole = mockRole(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(clinicAdminRole));
        when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId)))
                .isInstanceOf(PatientNotFoundException.class);
    }

    @Test
    void throwsNotStaffedAtClinicForACallerWithNoActiveRole() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of());

        assertThatThrownBy(() -> newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId)))
                .isInstanceOf(NotStaffedAtClinicException.class);
    }

    @Test
    void doctorOnlyCallerSeesOnlyTheirOwnEncountersWithThePatient() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID callerDoctorProfileId = UUID.randomUUID();
        var clinic = mockClinicWithId(clinicId);
        RoleAssignment doctorRole = mockRole(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        DoctorProfile callerDoctorProfile = org.mockito.Mockito.mock(DoctorProfile.class);
        when(callerDoctorProfile.getId()).thenReturn(callerDoctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.of(callerDoctorProfile));
        when(patient.getClinic()).thenReturn(clinic);
        when(patient.getId()).thenReturn(patientId);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        Page<Booking> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(
                        eq(patientId), eq(callerDoctorProfileId), any()))
                .thenReturn(emptyPage);

        var response = newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId));

        assertThat(response.totalCount()).isZero();
    }

    @Test
    void doctorOnlyCallerWithNoResolvableDoctorProfileFailsClosedToAnEmptyPage() {
        UUID callerAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        var clinic = mockClinicWithId(clinicId);
        RoleAssignment doctorRole = mockRole(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(callerAccountId, clinicId))
                .thenReturn(List.of(doctorRole));
        when(doctorProfileRepository.findByAccount_Id(callerAccountId)).thenReturn(Optional.empty());
        when(patient.getClinic()).thenReturn(clinic);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        var response = newController().list(clinicId, patientId, 0, 20, authenticationFor(callerAccountId));

        assertThat(response.totalCount()).isZero();
        assertThat(response.bookings()).isEmpty();
    }

    private static com.cms.identity.clinic.Clinic mockClinicWithId(UUID id) {
        var clinic = org.mockito.Mockito.mock(com.cms.identity.clinic.Clinic.class);
        when(clinic.getId()).thenReturn(id);
        return clinic;
    }
}
