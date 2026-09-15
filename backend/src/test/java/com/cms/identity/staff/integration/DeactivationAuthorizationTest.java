package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** T003: no token -> 401; non-ClinicAdmin token -> 403; cross-clinic ClinicAdmin -> 403 (FR-002). */
class DeactivationAuthorizationTest extends AbstractStaffIntegrationTest {

    @Test
    void rejectsRequestWithNoToken() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account operations = saveAccount("ops@sunrise-clinic.example", "Str0ng!Pass", "OP-1001");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                        "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate", clinic.getId(), operations.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsNonClinicAdminToken() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        String nonAdminToken = nonClinicAdminToken(clinic);
        Account target = saveAccount("target@sunrise-clinic.example", "Str0ng!Pass", "OP-2002");
        roleAssignmentRepository.save(new RoleAssignment(target, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate", clinic.getId(), target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonAdminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsClinicAdminOfADifferentClinic() throws Exception {
        Clinic targetClinic = saveClinic("Sunrise Clinic");
        Clinic otherClinic = saveClinic("Other Clinic");
        String otherAdminToken = clinicAdminToken(otherClinic);
        Account target = saveAccount("target@sunrise-clinic.example", "Str0ng!Pass", "OP-3003");
        roleAssignmentRepository.save(new RoleAssignment(target, targetClinic, RoleAssignment.Role.Operations));

        mockMvc.perform(post(
                                "/api/v1/clinics/{clinicId}/staff/{accountId}/deactivate",
                                targetClinic.getId(),
                                target.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherAdminToken))
                .andExpect(status().isForbidden());
    }
}
