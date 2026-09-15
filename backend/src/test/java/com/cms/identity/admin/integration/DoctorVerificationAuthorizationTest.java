package com.cms.identity.admin.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * T007: both endpoints reject requests with no credentials AND with valid staff-Account
 * credentials (ClinicAdmin/Doctor/Operations), both as 401 - the admin chain checks only
 * against the configured Super Admin credential (FR-006, SC-002).
 */
class DoctorVerificationAuthorizationTest extends AbstractAdminIntegrationTest {

    @Test
    void listRejectsRequestWithNoCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/admin/doctors").param("status", "PENDING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRejectsValidStaffAccountCredentials() throws Exception {
        saveStaffAccount("staff@sunrise-clinic.example", "Str0ng!Pass");

        mockMvc.perform(get("/api/v1/admin/doctors")
                        .param("status", "PENDING")
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basicAuthHeader("staff@sunrise-clinic.example", "Str0ng!Pass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyRejectsRequestWithNoCredentials() throws Exception {
        var profile = saveDoctorProfile("doc@example.com", "LIC-001", "ENT", false);

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", profile.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyRejectsValidStaffAccountCredentials() throws Exception {
        saveStaffAccount("staff@sunrise-clinic.example", "Str0ng!Pass");
        var profile = saveDoctorProfile("doc@example.com", "LIC-001", "ENT", false);

        mockMvc.perform(post("/api/v1/admin/doctors/{id}/verify", profile.getId())
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                basicAuthHeader("staff@sunrise-clinic.example", "Str0ng!Pass")))
                .andExpect(status().isUnauthorized());

        assertFalse(doctorProfileRepository.findById(profile.getId()).orElseThrow().isLicenseVerified());
    }
}
