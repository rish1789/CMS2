package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** Employee deactivation modal: reason is required, must be a known value, and is persisted on success. */
class DeactivationReasonTest extends AbstractStaffIntegrationTest {

    @Test
    void missingReasonIsRejected() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String adminToken = clinicAdminToken(clinic);
        Account operations = saveAccount("ops@sunrise-clinic.example", "Str0ng!Pass", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                clinic.getId(),
                                operations.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REASON"));
    }

    @Test
    void invalidReasonIsRejected() throws Exception {
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
                        .content("{\"reason\":\"NOT_A_REAL_REASON\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_REASON"));
    }

    @Test
    void validReasonIsPersisted() throws Exception {
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
                        .content("{\"reason\":\"SERVICE_NOT_REQUIRED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        RoleAssignment.DeactivationReason persisted = roleAssignmentRepository
                .findByAccount_IdAndClinic_Id(operations.getId(), clinic.getId())
                .orElseThrow()
                .getDeactivationReason();
        Assertions.assertEquals(RoleAssignment.DeactivationReason.SERVICE_NOT_REQUIRED, persisted);
    }
}
