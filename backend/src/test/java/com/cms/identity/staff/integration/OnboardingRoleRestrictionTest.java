package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T009: role=ClinicAdmin/SuperAdmin always rejected server-side (FR-003), even from a valid, authenticated ClinicAdmin caller. */
class OnboardingRoleRestrictionTest extends AbstractStaffIntegrationTest {

    @Test
    void rejectsClinicAdminRole() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "Sneaky Admin", "email": "sneaky@sunrise-clinic.example", "role": "ClinicAdmin" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ROLE"));

        assertThat(accountRepository.findByEmail("sneaky@sunrise-clinic.example")).isEmpty();
    }

    @Test
    void rejectsSuperAdminRole() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "Sneaky Super", "email": "sneaky-super@sunrise-clinic.example", "role": "SuperAdmin" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ROLE"));

        assertThat(accountRepository.findByEmail("sneaky-super@sunrise-clinic.example")).isEmpty();
    }
}
