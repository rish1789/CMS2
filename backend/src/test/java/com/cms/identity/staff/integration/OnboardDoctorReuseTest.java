package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T019: a second clinic onboarding the same license number + matching specialization
 * (case/whitespace-insensitive) reuses the existing global Account/Doctor Profile -
 * only a new Role Assignment, no new credentials, no new rows (FR-002, FR-002b, SC-006).
 */
class OnboardDoctorReuseTest extends AbstractStaffIntegrationTest {

    private static final String LICENSE_NUMBER = "LIC-REUSE-001";

    @Test
    void secondClinicOnboardingReusesExistingAccountAndProfile() throws Exception {
        Clinic clinicA = saveClinic("Sunrise Clinic");
        Clinic clinicB = saveClinic("Riverside Clinic");
        String tokenA = clinicAdminToken(clinicA);
        String tokenB = clinicAdminToken(clinicB);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(doctorRequestJson("dr.first@sunrise-clinic.example", "ent", LICENSE_NUMBER)))
                .andExpect(status().isCreated());

        DoctorProfile existing = doctorProfileRepository.findByLicenseNumber(LICENSE_NUMBER).orElseThrow();
        String existingAccountId = existing.getAccount().getId().toString();
        String existingStaffCode = existing.getAccount().getStaffCode();
        String existingDoctorProfileId = existing.getId().toString();

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                        // Different submitted email/specialization-casing on purpose: a second
                        // clinic's ClinicAdmin fills the form fresh, doesn't know the doctor's
                        // exact original email, and may type "ENT" differently.
                        .content(doctorRequestJson("dr.second@riverside-clinic.example", "ENT", LICENSE_NUMBER))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.existingAccount").value(true))
                .andExpect(jsonPath("$.temporaryPassword").value((Object) null))
                .andExpect(jsonPath("$.accountId").value(existingAccountId))
                .andExpect(jsonPath("$.staffCode").value(existingStaffCode))
                .andExpect(jsonPath("$.doctorProfileId").value(existingDoctorProfileId));

        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(doctorProfileRepository.count()).isEqualTo(1);
        assertThat(roleAssignmentRepository.count()).isEqualTo(3); // ClinicAdmin x2 + the reused Doctor
    }
}
