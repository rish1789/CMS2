package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * T014: "no override for any role, including Super Admin" (FR-004) - proved structurally,
 * not by attempting an override. This endpoint lives under {@code /api/v1/clinics/**},
 * which is JWT-protected (com.cms.identity.account.SecurityConfig, @Order(1)); Super
 * Admin's own chain (com.cms.identity.admin.SuperAdminSecurityConfig, @Order(3)) is
 * scoped only to {@code /api/v1/admin/**} and never even matches this request. Super
 * Admin's Basic Auth credentials carry no staff JWT, so presenting them here fails
 * exactly like any other unauthenticated request - there is no bridge between the two
 * identity systems for Super Admin to cross, structurally, not just by convention (mirrors
 * 002's FR-008 pattern).
 */
class LastActiveClinicAdminNoOverrideTest extends AbstractStaffIntegrationTest {

    @Test
    void superAdminCredentialsCannotReachThisEndpointAtAll() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account admin = saveAccount("admin@sunrise-clinic.example", "Str0ng!Pass", "CA-1001");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));

        // A real, valid Super Admin credential (per this sandbox's config) - presented
        // against a JWT-only endpoint, it is simply not a JWT, so it's rejected as
        // unauthenticated, the same as no credential at all.
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate", clinic.getId(), admin.getId())
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isUnauthorized());

        boolean stillActive = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(admin.getId(), clinic.getId())
                .orElseThrow()
                .isActive();
        org.junit.jupiter.api.Assertions.assertTrue(stillActive);
    }
}
