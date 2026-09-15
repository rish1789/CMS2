package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.admin.SuperAdminJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * T006: GET .../clinics?verified={true|false} returns only clinics matching the requested state (FR-001).
 *
 * <p>040-super-admin-rbac-login T011: the two tests below already prove this endpoint
 * accepts a Super Admin bearer JWT (not Basic Auth) via the inherited {@code
 * superAdminAuthHeader()}, migrated by T014a. {@code acceptsAnyValidlyIssuedSuperAdminToken}
 * additionally proves a token issued directly (not via login) is equally accepted, since
 * the filter chain only ever inspects the token itself.
 */
class PendingClinicsListTest extends AbstractAdminIntegrationTest {

    @Autowired
    private SuperAdminJwtService superAdminJwtService;

    @Test
    void acceptsAnyValidlyIssuedSuperAdminToken() throws Exception {
        saveClinic("Pending Clinic", false);

        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + superAdminJwtService.issueToken(SUPER_ADMIN_USERNAME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(1));
    }

    @Test
    void unverifiedFilterReturnsOnlyUnverifiedClinics() throws Exception {
        var pending = saveClinic("Pending Clinic", false);
        saveClinic("Already Verified Clinic", true);

        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(1))
                .andExpect(jsonPath("$.clinics[0].clinicId").value(pending.getId().toString()))
                .andExpect(jsonPath("$.clinics[0].name").value("Pending Clinic"));
    }

    @Test
    void verifiedFilterReturnsOnlyVerifiedClinics() throws Exception {
        saveClinic("Pending Clinic", false);
        var verified = saveClinic("Already Verified Clinic", true);

        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "VERIFIED")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(1))
                .andExpect(jsonPath("$.clinics[0].clinicId").value(verified.getId().toString()));
    }

    /** pagination-unification-2026-09-10: a small page reports the full totalCount, not just this page's size - the platform-wide queue grows unbounded across every clinic registration. */
    @Test
    void aSmallPageStillReportsTheFullTotalCount() throws Exception {
        saveClinic("Pending Clinic A", false);
        saveClinic("Pending Clinic B", false);
        saveClinic("Pending Clinic C", false);

        mockMvc.perform(get("/api/v1/admin/clinics")
                        .param("status", "PENDING")
                        .param("page", "0")
                        .param("size", "2")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics.length()").value(2))
                .andExpect(jsonPath("$.totalCount").value(3));
    }
}
