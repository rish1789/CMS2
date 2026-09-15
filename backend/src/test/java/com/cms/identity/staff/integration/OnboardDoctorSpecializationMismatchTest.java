package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T020: a license number matching an existing profile but a different specialization is
 * rejected as a conflict - zero rows created (FR-002a, SC-007). Example matches spec.md's
 * ENT-vs-Radiology illustration directly.
 */
class OnboardDoctorSpecializationMismatchTest extends AbstractStaffIntegrationTest {

    private static final String LICENSE_NUMBER = "LIC-MISMATCH-001";

    @Test
    void mismatchedSpecializationOnSameLicenseNumberIsRejected() throws Exception {
        Clinic clinicA = saveClinic("Sunrise Clinic");
        Clinic clinicB = saveClinic("Riverside Clinic");
        String tokenA = clinicAdminToken(clinicA);
        String tokenB = clinicAdminToken(clinicB);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.ent@sunrise-clinic.example", "ENT", LICENSE_NUMBER)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.radiology@riverside-clinic.example", "Radiology", LICENSE_NUMBER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SPECIALIZATION_MISMATCH"));

        assertThat(accountRepository.count()).isEqualTo(3); // ClinicAdmin x2 + the one successful Doctor onboarding
        assertThat(doctorProfileRepository.count()).isEqualTo(1);
        assertThat(roleAssignmentRepository.count()).isEqualTo(3); // ClinicAdmin x2 + the one successful Doctor
    }
}
