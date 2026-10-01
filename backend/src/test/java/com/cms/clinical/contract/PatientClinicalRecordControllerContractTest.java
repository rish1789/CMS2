package com.cms.clinical.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.clinical.api.PatientClinicalRecordController;
import com.cms.clinical.exception.ClinicalDocumentationExceptionHandler;
import com.cms.clinical.service.ClinicalRecordAvailabilityService;
import com.cms.clinical.service.ConsultationNoteService;
import com.cms.clinical.service.ExternalRecordReferenceService;
import com.cms.clinical.service.PrescriptionService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 059-patient-clinical-record-access (contracts/patient-clinical-record-access.md): web-layer
 * only, real JWT auth via a real token - mirrors SlotAppearedControllerContractTest's established
 * shape for a JWT-gated endpoint, adapted to the patient realm.
 */
@WebMvcTest(controllers = PatientClinicalRecordController.class)
@Import({ClinicalDocumentationExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientClinicalRecordControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ClinicalRecordAvailabilityService clinicalRecordAvailabilityService;

    @MockitoBean
    private ConsultationNoteService consultationNoteService;

    @MockitoBean
    private PrescriptionService prescriptionService;

    @MockitoBean
    private ExternalRecordReferenceService externalRecordReferenceService;

    @Test
    void returnsTheAvailableBookingIdsForAValidRequest() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        when(clinicalRecordAvailabilityService.findBookingIdsWithAnyRecord(any(), any()))
                .thenReturn(Set.of(bookingId));

        mockMvc.perform(get("/api/v1/patients/bookings/clinical-record-availability")
                        .param("bookingIds", bookingId.toString())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingIdsWithRecords[0]").value(bookingId.toString()));
    }

    @Test
    void missingBookingIdsReturns400() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);

        mockMvc.perform(get("/api/v1/patients/bookings/clinical-record-availability")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BOOKING_IDS_REQUIRED"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/patients/bookings/clinical-record-availability")
                        .param("bookingIds", UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
    }
}
