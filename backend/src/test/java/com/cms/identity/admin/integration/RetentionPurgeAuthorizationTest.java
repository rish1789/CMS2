package com.cms.identity.admin.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.Account;
import com.cms.identity.account.StaffJwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/**
 * 038 US2: T014-T016. Authorization-focused - the purge logic itself (which bookings get
 * purged) is already covered by {@code RetentionPurgeTest} (US1); this class only verifies who
 * may invoke the manual trigger.
 */
class RetentionPurgeAuthorizationTest extends AbstractAdminIntegrationTest {

    @Autowired
    private StaffJwtService staffJwtService;

    @Test
    void superAdminCanManuallyTriggerThePurge() throws Exception {
        mockMvc.perform(post("/api/v1/admin/retention-purge/run")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedBookingCount").exists());
    }

    @Test
    void rejectsRequestWithNoCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/admin/retention-purge/run")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRequestWithAStaffJwt() throws Exception {
        Account staff = saveStaffAccount("ops@sunrise-clinic.example", "Str0ng!Pass");
        String staffJwt = staffJwtService.issueToken(staff.getId());

        mockMvc.perform(post("/api/v1/admin/retention-purge/run")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwt))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void canBeInvokedAgainImmediatelyAfterAPriorRun() throws Exception {
        mockMvc.perform(post("/api/v1/admin/retention-purge/run")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/retention-purge/run")
                        .header(HttpHeaders.AUTHORIZATION, superAdminAuthHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.purgedBookingCount").exists());
    }
}
