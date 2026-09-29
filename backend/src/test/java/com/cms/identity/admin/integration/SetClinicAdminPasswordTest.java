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
import org.springframework.http.MediaType;

/**
 * real-bug-fix 2026-09-17: resetClinicAdminPassword only ever generates a random password -
 * this covers the sibling endpoint letting Super Admin set a specific chosen one instead.
 */
class SetClinicAdminPasswordTest extends AbstractAdminIntegrationTest {

    @Test
    void setsTheClinicAdminPasswordToTheChosenValue() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        Account admin = saveStaffAccount("admin@sunrise.example", "OldPassw0rd!");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/set-admin-password", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"newPassword\": \"starqweR@1\" }"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.email").value("admin@sunrise.example"))
                .andExpect(jsonPath("$.temporaryPassword").value("starqweR@1"));

        Account reloaded = accountRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("starqweR@1", reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPassw0rd!", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    void rejectsAPasswordThatFailsThePolicy() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic", true);
        Account admin = saveStaffAccount("admin@sunrise.example", "OldPassw0rd!");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/set-admin-password", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"newPassword\": \"weak\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));

        Account reloaded = accountRepository.findById(admin.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("OldPassw0rd!", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void settingAnUnknownClinicIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/admin/clinics/{id}/set-admin-password", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"newPassword\": \"starqweR@1\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_FOUND"));
    }

    @Test
    void settingAClinicWithNoActiveClinicAdminIsRejected() throws Exception {
        Clinic clinic = saveClinic("Orphan Clinic", true);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/set-admin-password", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"newPassword\": \"starqweR@1\" }"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("CLINIC_ADMIN_ACCOUNT_NOT_FOUND"));
    }
}
