package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * T015: with two active ClinicAdmin Role Assignments at one clinic - a state no current
 * feature builds a path to reach normally (001 creates exactly one at registration, 004
 * can't onboard a peer ClinicAdmin) - deactivating one succeeds and the other remains
 * active (FR-005). The second ClinicAdmin is inserted directly as a test fixture, not
 * through any API, precisely because no such API exists.
 */
class MultipleClinicAdminsDeactivationTest extends AbstractStaffIntegrationTest {

    @Test
    void deactivatingOneOfTwoActiveClinicAdminsSucceeds() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account firstAdmin = saveAccount("admin-1@sunrise-clinic.example", "Str0ng!Pass", "CA-1001");
        roleAssignmentRepository.save(new RoleAssignment(firstAdmin, clinic, RoleAssignment.Role.ClinicAdmin));
        Account secondAdmin = saveAccount("admin-2@sunrise-clinic.example", "Str0ng!Pass", "CA-1002");
        roleAssignmentRepository.save(new RoleAssignment(secondAdmin, clinic, RoleAssignment.Role.ClinicAdmin));
        String firstAdminToken = staffJwtService.issueToken(firstAdmin.getId());

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinic.getId(),
                                secondAdmin.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + firstAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"SERVICE_NOT_REQUIRED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        boolean firstAdminStillActive = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(firstAdmin.getId(), clinic.getId())
                .orElseThrow()
                .isActive();
        org.junit.jupiter.api.Assertions.assertTrue(firstAdminStillActive);
    }
}
