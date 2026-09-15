package com.cms.identity.integration;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** T011: happy path - registration creates Clinic + Account + RoleAssignment atomically. */
class RegisterClinicHappyPathTest extends AbstractIntegrationTest {

    @Test
    void registrationCreatesClinicAccountAndRoleAssignmentAtomically() throws Exception {
        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson("owner@sunrise-clinic.example")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.verified", is(false)))
                .andExpect(jsonPath("$.clinicName", is("Sunrise Clinic")))
                .andExpect(jsonPath("$.admin.email", is("owner@sunrise-clinic.example")))
                .andExpect(jsonPath("$.admin.staffCode", is(not(emptyOrNullString()))));

        org.junit.jupiter.api.Assertions.assertEquals(1, clinicRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, accountRepository.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, roleAssignmentRepository.count());

        var clinic = clinicRepository.findAll().get(0);
        var account = accountRepository.findAll().get(0);
        var roleAssignment = roleAssignmentRepository.findAll().get(0);

        org.junit.jupiter.api.Assertions.assertFalse(clinic.isVerified());
        org.junit.jupiter.api.Assertions.assertTrue(account.isActive());
        org.junit.jupiter.api.Assertions.assertEquals(
                com.cms.identity.account.RoleAssignment.Role.ClinicAdmin, roleAssignment.getRole());
        org.junit.jupiter.api.Assertions.assertEquals(
                account.getId(), roleAssignment.getAccount().getId());
        org.junit.jupiter.api.Assertions.assertEquals(
                clinic.getId(), roleAssignment.getClinic().getId());
    }
}
