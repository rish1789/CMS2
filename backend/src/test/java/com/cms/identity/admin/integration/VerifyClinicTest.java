package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T007: verify sets true; repeating the call on an already-verified clinic is idempotent (FR-002, FR-007). */
class VerifyClinicTest extends AbstractAdminIntegrationTest {

    @Test
    void verifyingAnUnverifiedClinicSetsVerifiedTrue() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", false);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/verify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinicId").value(clinic.getId().toString()))
                .andExpect(jsonPath("$.verified").value(true));

        assertTrue(clinicRepository.findById(clinic.getId()).orElseThrow().isVerified());
    }

    @Test
    void verifyingAnAlreadyVerifiedClinicIsIdempotent() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", true);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/verify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true));

        assertTrue(clinicRepository.findById(clinic.getId()).orElseThrow().isVerified());
    }
}
