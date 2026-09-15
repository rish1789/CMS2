package com.cms.identity.account.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.RoleAssignment;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** 041-staff-console-pickers T002/US1: GET /api/v1/clinics/mine lists only the caller's active-role clinics. */
class StaffClinicControllerTest extends AbstractStaffIntegrationTest {

    @Autowired
    private StaffJwtService staffJwtService;

    @Test
    void listsOnlyActiveClinicsForTheCaller() throws Exception {
        Clinic clinicA = saveClinic("Sunrise Clinic");
        Clinic clinicB = saveClinic("Riverside Clinic");
        var account = saveAccount("multi.clinic@example.com", "Str0ng!Pass", "OP-1000");
        roleAssignmentRepository.save(new RoleAssignment(account, clinicA, RoleAssignment.Role.Operations));
        roleAssignmentRepository.save(new RoleAssignment(account, clinicB, RoleAssignment.Role.ClinicAdmin));
        String token = staffJwtService.issueToken(account.getId());

        mockMvc.perform(get("/api/v1/clinics/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(2))
                .andExpect(jsonPath("$.clinics[*].name").value(org.hamcrest.Matchers.containsInAnyOrder(
                        "Sunrise Clinic", "Riverside Clinic")));
    }

    @Test
    void returnsEmptyListForAnAccountWithNoActiveRoles() throws Exception {
        var account = saveAccount("no.clinics@example.com", "Str0ng!Pass", "OP-1001");
        String token = staffJwtService.issueToken(account.getId());

        mockMvc.perform(get("/api/v1/clinics/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(0));
    }

    @Test
    void excludesADeactivatedRoleAssignment() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        var account = saveAccount("deactivated.role@example.com", "Str0ng!Pass", "OP-1002");
        RoleAssignment roleAssignment = new RoleAssignment(account, clinic, RoleAssignment.Role.Operations);
        roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
        roleAssignmentRepository.save(roleAssignment);
        String token = staffJwtService.issueToken(account.getId());

        mockMvc.perform(get("/api/v1/clinics/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(0));
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        var account = saveAccount("many.clinics@example.com", "Str0ng!Pass", "OP-1003");
        for (int i = 0; i < 3; i++) {
            Clinic clinic = saveClinic("Clinic " + i);
            roleAssignmentRepository.save(new RoleAssignment(account, clinic, RoleAssignment.Role.Operations));
        }
        String token = staffJwtService.issueToken(account.getId());

        mockMvc.perform(get("/api/v1/clinics/mine")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(2));
    }
}
