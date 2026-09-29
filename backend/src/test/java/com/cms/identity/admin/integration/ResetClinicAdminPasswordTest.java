package com.cms.identity.admin.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * real-bug-fix 2026-09-16: a verified clinic's ClinicAdmin login stopped working with no way to
 * recover it - this system has no self-service "forgot password" flow at all. Mirrors
 * StaffOnboardingService's own "generate + hash + return once" contract exactly.
 */
class ResetClinicAdminPasswordTest extends AbstractAdminIntegrationTest {

    @Test
    void resetsTheClinicAdminPasswordAndReturnsItExactlyOnce() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        Account admin = saveStaffAccount("admin@sunrise.example", "OldPassw0rd!");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));

        String response = mockMvc.perform(post("/api/v1/admin/clinics/{id}/reset-admin-password", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.email").value("admin@sunrise.example"))
                .andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String newTemporaryPassword = com.jayway.jsonpath.JsonPath.read(response, "$.temporaryPassword");
        Account reloaded = accountRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(newTemporaryPassword, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassw0rd!", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    void resettingAnUnknownClinicIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/clinics/{id}/reset-admin-password", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_FOUND"));
    }

    @Test
    void resettingAClinicWithNoActiveClinicAdminIsRejected() throws Exception {
        Clinic clinic = saveClinic("Orphan Clinic", true);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/reset-admin-password", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_ADMIN_ACCOUNT_NOT_FOUND"));
    }
}
