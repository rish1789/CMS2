package com.cms.patient.record.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.record.Patient;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * staff-console-audit-2026-09-10 P1: GET /api/v1/clinics/{clinicId}/patients/{patientId} - the
 * entity-context lookup backing the Anonymize page's header (previously a bare red button with
 * no indication of whose record it was about to erase).
 */
class PatientDetailControllerTest extends AbstractPatientAnonymizationIntegrationTest {

    @Test
    void returnsTheNamedPatient() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patientId").value(patient.getId().toString()))
                .andExpect(jsonPath("$.name").value(patient.getName()))
                .andExpect(jsonPath("$.phone").value(patient.getPhone()));
    }

    @Test
    void unknownPatientIdIsNotFound() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}", clinic.getId(), UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void aPatientAtAnotherClinicIsNotFound() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);
        Clinic otherClinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}", otherClinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(otherClinic)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PATIENT_NOT_FOUND"));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);
        Clinic other = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(other)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void stillResolvesAnAlreadyAnonymizedPatientRatherThanErroring() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);
        patientAnonymizationService.anonymize(clinic.getId(), patient.getId());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/{patientId}", clinic.getId(), patient.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Anonymized Patient"));
    }
}
