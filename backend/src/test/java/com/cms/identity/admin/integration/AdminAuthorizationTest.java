package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.StaffJwtService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * T008: both endpoints reject requests with no credentials AND with a valid 001 staff
 * Account's own credential, both as 401 - the admin chain checks only against a
 * {@code SUPER_ADMIN}-audience JWT, never against AccountRepository (FR-004/FR-009).
 *
 * <p>040-super-admin-rbac-login (T027): the staff-rejection cases now present a valid
 * staff JWT rather than a staff Basic Auth header, since Basic Auth is no longer a
 * meaningful thing to send at all (proven separately below, FR-012).
 */
class AdminAuthorizationTest extends AbstractAdminIntegrationTest {

    @Autowired
    private StaffJwtService staffJwtService;

    @Autowired
    private com.cms.patient.account.JwtService patientJwtService;

    @Test
    void listRejectsRequestWithNoCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clinics").param("status", "PENDING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listRejectsValidStaffJwt() throws Exception {
        Account staff = saveStaffAccount("staff@sunrise-clinic.example", "Str0ng!Pass");

        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(staff.getId())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyRejectsRequestWithNoCredentials() throws Exception {
        var clinic = saveClinic("Sunrise Clinic", false);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/verify", clinic.getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void verifyRejectsValidStaffJwt() throws Exception {
        Account staff = saveStaffAccount("staff@sunrise-clinic.example", "Str0ng!Pass");
        var clinic = saveClinic("Sunrise Clinic", false);

        mockMvc.perform(post("/api/v1/admin/clinics/{id}/verify", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(staff.getId())))
                .andExpect(status().isUnauthorized());

        org.junit.jupiter.api.Assertions.assertFalse(
                clinicRepository.findById(clinic.getId()).orElseThrow().isVerified());
    }

    @Test
    void correctCredentialBasicAuthIsNoLongerAccepted() throws Exception {
        // FR-012: Basic Auth was the sole mechanism before this feature - now rejected
        // outright, even with the exact correct Super Admin username/password.
        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, basicAuthHeader(SUPER_ADMIN_USERNAME, SUPER_ADMIN_PASSWORD)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void malformedSuperAdminTokenRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validPatientAccountJwtRejected() throws Exception {
        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientJwtService.issueToken(UUID.randomUUID())))
                .andExpect(status().isUnauthorized());
    }
}
