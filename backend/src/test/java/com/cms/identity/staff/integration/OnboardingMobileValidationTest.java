package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T012: invalid-format mobile rejected; omitted mobile succeeds (FR-008). */
class OnboardingMobileValidationTest extends AbstractStaffIntegrationTest {

    @Test
    void rejectsInvalidMobileFormat() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "Bad Mobile", "email": "bad.mobile@sunrise-clinic.example", "role": "Operations", "mobile": "12345" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_MOBILE_NUMBER"));
    }

    @Test
    void succeedsWithNoMobileProvided() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson("no.mobile@sunrise-clinic.example")))
                .andExpect(status().isCreated());
    }

    @Test
    void succeedsWithValidIndianMobile() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "Good Mobile", "email": "good.mobile@sunrise-clinic.example", "role": "Operations", "mobile": "9876543210" }
                                """))
                .andExpect(status().isCreated());
    }
}
