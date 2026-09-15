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

/** T002: deactivating an active Doctor/Operations Role Assignment succeeds (FR-001). */
class DeactivateStaffHappyPathTest extends AbstractStaffIntegrationTest {

    @Test
    void deactivatesAnActiveOperationsRoleAssignment() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account operations = saveAccount("ops@sunrise-clinic.example", "Str0ng!Pass", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinic.getId(),
                                operations.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"RESIGNED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.role").value("Operations"));

        boolean stillActive = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(operations.getId(), clinic.getId())
                .orElseThrow()
                .isActive();
        org.junit.jupiter.api.Assertions.assertFalse(stillActive);
    }
}
