package com.cms.patient.record.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.api.PatientBookingHistoryController;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.exception.PatientRecordExceptionHandler;
import com.cms.patient.record.repository.PatientRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 052-patient-clinical-hub T002 (contracts/patient-booking-history.md): web-layer only (mocked
 * repositories), real JWT auth via a real token - mirrors
 * TodaySessionStatsControllerContractTest's own established shape (051) for a staff-JWT-gated
 * endpoint with no service layer.
 *
 * <p>doctor-console-cross-doctor-leak fix: also covers the doctor self-scoping now applied here.
 */
@WebMvcTest(controllers = PatientBookingHistoryController.class)
@Import({
    com.cms.support.AllowAllStaffSessionsTestConfig.class,
    PatientRecordExceptionHandler.class,
    SecurityConfig.class,
    StaffAuthenticationEntryPoint.class,
    StaffJwtService.class
})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientBookingHistoryControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private PatientRepository patientRepository;

    @MockitoBean
    private RoleAssignmentRepository roleAssignmentRepository;

    @MockitoBean
    private DoctorProfileRepository doctorProfileRepository;

    @Test
    void returnsThePatientsBookingsForAnActiveStaffMember() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        Clinic clinic = Mockito.mock(Clinic.class);
        when(clinic.getId()).thenReturn(clinicId);
        Patient patient = Mockito.mock(Patient.class);
        when(patient.getClinic()).thenReturn(clinic);
        when(patient.getId()).thenReturn(patientId);

        RoleAssignment clinicAdminRole = Mockito.mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of(clinicAdminRole));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(eq(patientId), isNull(), any()))
                .thenReturn(new PageImpl<Booking>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}/bookings", clinicId, patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.bookings").isArray());
    }

    @Test
    void aDoctorOnlyCallerSeesOnlyTheirOwnEncountersWithThePatient() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        UUID doctorProfileId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        Clinic clinic = Mockito.mock(Clinic.class);
        when(clinic.getId()).thenReturn(clinicId);
        Patient patient = Mockito.mock(Patient.class);
        when(patient.getClinic()).thenReturn(clinic);
        when(patient.getId()).thenReturn(patientId);

        RoleAssignment doctorRole = Mockito.mock(RoleAssignment.class);
        when(doctorRole.getRole()).thenReturn(RoleAssignment.Role.Doctor);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of(doctorRole));
        DoctorProfile doctorProfile = Mockito.mock(DoctorProfile.class);
        when(doctorProfile.getId()).thenReturn(doctorProfileId);
        when(doctorProfileRepository.findByAccount_Id(accountId)).thenReturn(Optional.of(doctorProfile));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
        when(bookingRepository.findByPatient_IdOrderBySlot_Session_SessionDateDesc(
                        eq(patientId), eq(doctorProfileId), any()))
                .thenReturn(new PageImpl<Booking>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}/bookings", clinicId, patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    void returnsNotFoundForAPatientBelongingToADifferentClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID otherClinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        Clinic otherClinic = Mockito.mock(Clinic.class);
        when(otherClinic.getId()).thenReturn(otherClinicId);
        Patient patient = Mockito.mock(Patient.class);
        when(patient.getClinic()).thenReturn(otherClinic);

        RoleAssignment clinicAdminRole = Mockito.mock(RoleAssignment.class);
        when(clinicAdminRole.getRole()).thenReturn(RoleAssignment.Role.ClinicAdmin);
        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of(clinicAdminRole));
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}/bookings", clinicId, patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        when(roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(eq(accountId), eq(clinicId)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}/bookings", clinicId, patientId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/patients/{patientId}/bookings",
                        UUID.randomUUID(),
                        UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
