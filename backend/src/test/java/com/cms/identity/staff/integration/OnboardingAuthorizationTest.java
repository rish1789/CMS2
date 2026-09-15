package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T010: no token -> 401; valid non-ClinicAdmin staff token -> 403 (FR-002). */
class OnboardingAuthorizationTest extends AbstractStaffIntegrationTest {

    @Test
    void rejectsRequestWithNoToken() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson("nobody@sunrise-clinic.example")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonClinicAdminToken() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = nonClinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson("someone@sunrise-clinic.example")))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsClinicAdminOfADifferentClinic() throws Exception {
        Clinic targetClinic = saveClinic("Sunrise Clinic");
        Clinic otherClinic = saveClinic("Other Clinic");
        String otherAdminToken = clinicAdminToken(otherClinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", targetClinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson("cross-clinic@sunrise-clinic.example")))
                .andExpect(status().isForbidden());
    }
}
