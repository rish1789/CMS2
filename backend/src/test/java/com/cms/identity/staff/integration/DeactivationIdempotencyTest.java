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

/** T004: repeating a deactivation on an already-inactive Role Assignment succeeds unchanged, no error (FR-007). */
class DeactivationIdempotencyTest extends AbstractStaffIntegrationTest {

    @Test
    void repeatedDeactivationSucceedsUnchanged() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account operations = saveAccount("ops@sunrise-clinic.example", "Str0ng!Pass", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post(
                                    "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                    clinic.getId(),
                                    operations.getId())
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"RESIGNED\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.active").value(false));
        }
    }
}
