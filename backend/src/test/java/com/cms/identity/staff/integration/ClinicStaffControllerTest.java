package com.cms.identity.staff.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 041-staff-console-pickers T033/US4: active staff (any role) at a clinic, replacing a typed Account ID. */
class ClinicStaffControllerTest extends AbstractStaffIntegrationTest {

    @Test
    void listsActiveStaffOfAnyRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account admin = saveAccount("admin@example.com", "Str0ng!Pass", "CA-0001");
        Account doctor = saveAccount("doctor@example.com", "Str0ng!Pass", "DR-0001");
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        roleAssignmentRepository.save(new RoleAssignment(doctor, clinic, RoleAssignment.Role.Doctor));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.length()").value(3)); // admin + doctor + clinicAdminToken's own admin
    }

    @Test
    void excludesADeactivatedRoleAssignment() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account operations = saveAccount("ops@example.com", "Str0ng!Pass", "OP-0001");
        RoleAssignment roleAssignment = new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations);
        roleAssignment.deactivate(com.cms.identity.account.RoleAssignment.DeactivationReason.RESIGNED);
        roleAssignmentRepository.save(roleAssignment);

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.length()").value(1)); // only clinicAdminToken's own admin
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Clinic other = saveClinic("Other Clinic");

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(other)))
                .andExpect(status().isForbidden());
    }

    /**
     * pagination-unification-2026-09-10: the Roster's search/role/status/specialization filters
     * now run server-side (RoleAssignmentRepository.search), not client-side over a full fetch -
     * a small page must still report the full totalCount for the *filtered* set, not the whole
     * clinic roster.
     */
    @Test
    void aSmallPageStillReportsTheFullTotalCountOfTheFilteredSet() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        for (int i = 0; i < 3; i++) {
            Account doctor = saveAccount("doctor" + i + "@example.com", "Str0ng!Pass", "DR-100" + i);
            roleAssignmentRepository.save(new RoleAssignment(doctor, clinic, RoleAssignment.Role.Doctor));
        }
        Account operations = saveAccount("ops@example.com", "Str0ng!Pass", "OP-2000");
        roleAssignmentRepository.save(new RoleAssignment(operations, clinic, RoleAssignment.Role.Operations));

        // role=Doctor excludes both the seeded Operations account AND clinicAdminToken's own ClinicAdmin row.
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .param("role", "Doctor")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    /** pagination-unification-2026-09-10: the search filter matches name OR staff code, server-side. */
    @Test
    void theSearchFilterMatchesByNameOrStaffCode() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account priya = saveAccount("priya@example.com", "Str0ng!Pass", "DR-7777");
        roleAssignmentRepository.save(new RoleAssignment(priya, clinic, RoleAssignment.Role.Doctor));
        Account other = saveAccount("other@example.com", "Str0ng!Pass", "OP-8888");
        roleAssignmentRepository.save(new RoleAssignment(other, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .param("q", "7777")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.length()").value(1))
                .andExpect(jsonPath("$.staff[0].staffCode").value("DR-7777"));
    }

    /** pagination-unification-2026-09-10: sortBy=joinedAt actually orders the result server-side, not just within one already-fetched page. */
    @Test
    void sortByJoinedAtOrdersResultsServerSide() throws Exception {
        Clinic clinic = saveClinic("Sunrise Clinic");
        Account first = saveAccount("first@example.com", "Str0ng!Pass", "OP-0001");
        roleAssignmentRepository.save(new RoleAssignment(first, clinic, RoleAssignment.Role.Operations));
        Account second = saveAccount("second@example.com", "Str0ng!Pass", "OP-0002");
        roleAssignmentRepository.save(new RoleAssignment(second, clinic, RoleAssignment.Role.Operations));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/staff", clinic.getId())
                        .param("role", "Operations")
                        .param("sortBy", "joinedAt")
                        .param("sortDir", "desc")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff[0].staffCode").value("OP-0002"))
                .andExpect(jsonPath("$.staff[1].staffCode").value("OP-0001"));
    }
}
