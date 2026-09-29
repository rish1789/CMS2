package com.cms.identity.staff.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * real-bug-fix 2026-09-17: a ClinicAdmin resets/sets a Doctor or Operations staff member's
 * password at their own clinic - mirrors DeactivationAuthorizationTest/DeactivateStaffHappyPathTest's
 * own shape for the identical URL pattern.
 */
class StaffPasswordResetTest extends AbstractStaffIntegrationTest {

    @Test
    void resetsAnActiveDoctorsPasswordAndTheyCanLogInWithIt() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account doctor = saveAccount("doctor@sunrise-clinic.example", "OldPass!123", "DR-4467");
        roleAssignmentRepository.save(new RoleAssignment(doctor, clinic, RoleAssignment.Role.Doctor));

        String body = mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/reset-password", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(doctor.getId().toString()))
                .andExpect(jsonPath("$.email").value("doctor@sunrise-clinic.example"))
                .andExpect(jsonPath("$.staffCode").value("DR-4467"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String temporaryPassword = com.jayway.jsonpath.JsonPath.read(body, "$.temporaryPassword");
        Account reloaded = accountRepository.findById(doctor.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(temporaryPassword, reloaded.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("OldPass!123", reloaded.getPasswordHash())).isFalse();
    }

    @Test
    void setsASpecificChosenPasswordForAnOperationsAccount() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account operations = saveAccount("ops@sunrise-clinic.example", "OldPass!123", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/set-password", clinic.getId(), operations.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"Chosen!Pass123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.temporaryPassword").value("Chosen!Pass123"));

        Account reloaded = accountRepository.findById(operations.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("Chosen!Pass123", reloaded.getPasswordHash())).isTrue();
    }

    @Test
    void setPasswordRejectsAPolicyViolatingChoice() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account operations = saveAccount("ops@sunrise-clinic.example", "OldPass!123", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/set-password", clinic.getId(), operations.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"weak\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));
    }

    @Test
    void rejectsANonClinicAdminCaller() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String nonAdminToken = nonClinicAdminToken(clinic);
        Account doctor = saveAccount("doctor@sunrise-clinic.example", "OldPass!123", "DR-4467");
        roleAssignmentRepository.save(new RoleAssignment(doctor, clinic, RoleAssignment.Role.Doctor));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/reset-password", clinic.getId(), doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAClinicAdminOfADifferentClinic() throws Exception {
        Clinic targetClinic = saveClinic("Sunrise Clinic");
        Clinic otherClinic = saveClinic("Other Clinic");
        String otherAdminToken = clinicAdminToken(otherClinic);
        Account doctor = saveAccount("doctor@sunrise-clinic.example", "OldPass!123", "DR-4467");
        roleAssignmentRepository.save(new RoleAssignment(doctor, targetClinic, RoleAssignment.Role.Doctor));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/reset-password",
                                targetClinic.getId(),
                                doctor.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsResettingAFellowClinicAdminsPasswordWithNoOverride() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account otherAdmin = saveAccount("other-admin@sunrise-clinic.example", "OldPass!123", "CA-9001");
        roleAssignmentRepository.save(new RoleAssignment(otherAdmin, clinic, RoleAssignment.Role.ClinicAdmin));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/reset-password",
                                clinic.getId(),
                                otherAdmin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void anUnknownTargetAccountIsNotFound() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/reset-password",
                                clinic.getId(),
                                java.util.UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
