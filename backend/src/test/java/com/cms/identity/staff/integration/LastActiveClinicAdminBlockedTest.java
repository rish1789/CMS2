package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * T013: deactivating a clinic's only active ClinicAdmin is blocked (FR-003, FR-004), and
 * the check is scoped per clinic (FR-008) - a second, independent clinic's own single
 * ClinicAdmin doesn't affect the first clinic's block, and vice versa.
 */
class LastActiveClinicAdminBlockedTest extends AbstractStaffIntegrationTest {

    @Test
    void blocksDeactivatingTheOnlyActiveClinicAdmin() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account admin = saveAccount("admin@sunrise-clinic.example", "Str0ng!Pass", "CA-1001");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        String adminToken = staffJwtService.issueToken(admin.getId());

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate", clinic.getId(), admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ACTIVE_CLINIC_ADMIN"));

        boolean stillActive = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(admin.getId(), clinic.getId())
                .orElseThrow()
                .isActive();
        org.junit.jupiter.api.Assertions.assertTrue(stillActive);
    }

    @Test
    void perClinicScoping_secondClinicsAdminCountDoesNotAffectTheFirst() throws Exception {
        // Two independent clinics, each with exactly one active ClinicAdmin.
        Clinic clinicA = saveClinic("Clinic A");
        Account adminA = saveAccount("admin-a@example.com", "Str0ng!Pass", "CA-A001");
        roleAssignmentRepository.save(new RoleAssignment(adminA, clinicA, RoleAssignment.Role.ClinicAdmin));
        String adminATokenValue = staffJwtService.issueToken(adminA.getId());

        Clinic clinicB = saveClinic("Clinic B");
        Account adminB = saveAccount("admin-b@example.com", "Str0ng!Pass", "CA-B001");
        roleAssignmentRepository.save(new RoleAssignment(adminB, clinicB, RoleAssignment.Role.ClinicAdmin));
        String adminBTokenValue = staffJwtService.issueToken(adminB.getId());

        // Clinic A's sole admin is still blocked, unaffected by Clinic B having its own separate sole admin.
        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinicA.getId(),
                                adminA.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminATokenValue))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ACTIVE_CLINIC_ADMIN"));

        // Clinic B's sole admin is likewise still blocked - the count is genuinely per-clinic, not global.
        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinicB.getId(),
                                adminB.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminBTokenValue))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("LAST_ACTIVE_CLINIC_ADMIN"));
    }
}
