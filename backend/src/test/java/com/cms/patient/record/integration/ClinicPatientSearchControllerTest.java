package com.cms.patient.record.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.patient.record.Patient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 041-staff-console-pickers T025/US3: patient search by name or phone, clinic-scoped. */
class ClinicPatientSearchControllerTest extends AbstractPatientAnonymizationIntegrationTest {

    @Test
    void matchesByPartialName() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", patient.getName().substring(0, 6))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1))
                .andExpect(jsonPath("$.patients[0].patientId").value(patient.getId().toString()));
    }

    @Test
    void matchesByPhone() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", patient.getPhone())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(1));
    }

    @Test
    void returnsEmptyArrayForNoMatches() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", "zzznomatch")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(0));
    }

    @Test
    void rejectsATermUnderTwoCharacters() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", "a")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void neverReturnsAnAlreadyAnonymizedPatientEvenWhenTheTermMatchesItsPlaceholderName() throws Exception {
        Clinic clinic = saveClinic();
        Patient patient = savePatient(clinic, null);
        patientAnonymizationService.anonymize(clinic.getId(), patient.getId());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", "Anonymized")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(0));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();
        Clinic other = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", "test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(other)))
                .andExpect(status().isForbidden());
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        Clinic clinic = saveClinic();
        savePatient(clinic, null);
        savePatient(clinic, null);
        savePatient(clinic, null);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/patients/search", clinic.getId())
                        .param("q", "Test Patient")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.patients.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }
}
