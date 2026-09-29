package com.cms.clinical.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.BookingNotFoundException;
import com.cms.clinical.api.PatientClinicalRecordController;
import com.cms.clinical.domain.ExternalRecordReference;
import com.cms.clinical.exception.ClinicalDocumentationExceptionHandler;
import com.cms.clinical.service.ClinicalRecordAvailabilityService;
import com.cms.clinical.service.ConsultationNoteService;
import com.cms.clinical.service.ExternalRecordReferenceService;
import com.cms.clinical.service.PrescriptionService;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

/** 059-patient-clinical-record-access (contracts/patient-clinical-record-access.md): the external-record-references endpoint. */
@WebMvcTest(controllers = PatientClinicalRecordController.class)
@Import({
    ClinicalDocumentationExceptionHandler.class,
    BookingExceptionHandler.class,
    SecurityConfig.class,
    PatientAuthenticationEntryPoint.class,
    JwtService.class
})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientExternalRecordReferenceControllerContractTest {

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
    void returnsEveryReferenceWhenSomeExist() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        ExternalRecordReference reference = Mockito.mock(ExternalRecordReference.class);
        Booking booking = Mockito.mock(Booking.class);
        DoctorProfile doctor = Mockito.mock(DoctorProfile.class);
        when(reference.getId()).thenReturn(UUID.randomUUID());
        when(reference.getBooking()).thenReturn(booking);
        when(booking.getId()).thenReturn(bookingId);
        when(reference.getDoctorProfile()).thenReturn(doctor);
        when(doctor.getId()).thenReturn(UUID.randomUUID());
        when(reference.getRecordType()).thenReturn("Lab Report");
        when(reference.getSourceProvider()).thenReturn("City Diagnostics");
        when(reference.getRecordDate()).thenReturn(LocalDate.of(2026, 8, 1));
        when(reference.getSummary()).thenReturn("Blood panel reviewed.");
        when(reference.getCreatedAt()).thenReturn(Instant.now());
        when(externalRecordReferenceService.listForPatient(bookingId, patientAccountId)).thenReturn(List.of(reference));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/external-record-references", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recordType").value("Lab Report"));
    }

    @Test
    void returnsAnEmptyArrayWhenNoneExist() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        when(externalRecordReferenceService.listForPatient(bookingId, patientAccountId)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/external-record-references", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void returns404ForABookingThatIsNotTheCallersOwn() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(externalRecordReferenceService.listForPatient(any(), any())).thenThrow(new BookingNotFoundException(bookingId));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/external-record-references", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/external-record-references", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
