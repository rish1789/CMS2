package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** T008: Operations onboarding happy path - Account + RoleAssignment created and active, credentials returned, no DoctorProfile. */
class OnboardOperationsHappyPathTest extends AbstractStaffIntegrationTest {

    @Test
    void onboardsOperationsStaffSuccessfully() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String token = clinicAdminToken(clinic);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOperationsRequestJson("ops.hire@sunrise-clinic.example")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").exists())
                .andExpect(jsonPath("$.staffCode").exists())
                .andExpect(jsonPath("$.temporaryPassword").exists())
                .andExpect(jsonPath("$.role").value("Operations"))
                .andExpect(jsonPath("$.doctorProfileId").doesNotExist());

        assertThat(accountRepository.findByEmail("ops.hire@sunrise-clinic.example")).isPresent();
        var account = accountRepository.findByEmail("ops.hire@sunrise-clinic.example").orElseThrow();
        var roleAssignments = roleAssignmentRepository.findAll().stream()
                .filter(ra -> ra.getAccount().getId().equals(account.getId()))
                .toList();
        assertThat(roleAssignments).hasSize(1);
        assertThat(roleAssignments.get(0).getRole()).isEqualTo(RoleAssignment.Role.Operations);
        assertThat(roleAssignments.get(0).isActive()).isTrue();
        assertThat(doctorProfileRepository.count()).isEqualTo(0);
    }
}
