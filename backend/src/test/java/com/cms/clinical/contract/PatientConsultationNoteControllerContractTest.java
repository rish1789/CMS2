package com.cms.clinical.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.clinical.api.PatientClinicalRecordController;
import com.cms.clinical.domain.ConsultationNote;
import com.cms.clinical.exception.ClinicalDocumentationExceptionHandler;
import com.cms.clinical.service.ClinicalRecordAvailabilityService;
import com.cms.clinical.service.ConsultationNoteService;
import com.cms.clinical.service.ExternalRecordReferenceService;
import com.cms.clinical.service.PrescriptionService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 059-patient-clinical-record-access (contracts/patient-clinical-record-access.md): the consultation-note endpoint. */
@WebMvcTest(controllers = PatientClinicalRecordController.class)
@Import({
    ClinicalDocumentationExceptionHandler.class,
    BookingExceptionHandler.class,
    SecurityConfig.class,
    PatientAuthenticationEntryPoint.class,
    JwtService.class
})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientConsultationNoteControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private ClinicalRecordAvailabilityService clinicalRecordAvailabilityService;

    @MockBean
    private ConsultationNoteService consultationNoteService;

    @MockBean
    private PrescriptionService prescriptionService;

    @MockBean
    private ExternalRecordReferenceService externalRecordReferenceService;

    @Test
    void returnsTheNoteBodyWhenOneExists() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        ConsultationNote note = Mockito.mock(ConsultationNote.class);
        when(note.getId()).thenReturn(UUID.randomUUID());
        when(note.getBooking()).thenReturn(Mockito.mock(com.cms.booking.domain.Booking.class));
        when(note.getBooking().getId()).thenReturn(bookingId);
        when(note.getDoctorProfile()).thenReturn(Mockito.mock(com.cms.identity.doctor.DoctorProfile.class));
        when(note.getDoctorProfile().getId()).thenReturn(UUID.randomUUID());
        when(note.getContent()).thenReturn("Discussed symptoms, prescribed rest.");
        when(note.getCreatedAt()).thenReturn(java.time.Instant.now());
        when(consultationNoteService.getForPatient(bookingId, patientAccountId)).thenReturn(Optional.of(note));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Discussed symptoms, prescribed rest."));
    }

    @Test
    void returnsANullBodyWhenNoNoteExists() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        when(consultationNoteService.getForPatient(bookingId, patientAccountId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(""));
    }

    @Test
    void returns404ForABookingThatIsNotTheCallersOwn() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        when(consultationNoteService.getForPatient(any(), any())).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/consultation-note", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
