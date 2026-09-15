package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T027: role=Doctor with missing specialization/license/experience rejected 400 MISSING_REQUIRED_FIELD. */
class OnboardDoctorMissingFieldsTest extends AbstractStaffIntegrationTest {

    @Test
    void rejectsDoctorWithNoDoctorFieldsAtAll() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "No Fields", "email": "no.fields@sunrise-clinic.example", "role": "Doctor" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELD"));

        assertThat(accountRepository.findByEmail("no.fields@sunrise-clinic.example")).isEmpty();
    }

    @Test
    void rejectsDoctorWithMissingLicenseNumber() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                {
                                  "name": "Missing License",
                                  "email": "missing.license@sunrise-clinic.example",
                                  "role": "Doctor",
                                  "doctor": { "specialization": "Cardiology", "experienceYears": 5 }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELD"))
                .andExpect(jsonPath("$.field").value("doctor.licenseNumber"));
    }
}
