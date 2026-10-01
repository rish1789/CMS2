package com.cms.protection.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.protection.api.ClinicProtectionFlagController;
import com.cms.protection.dto.FlagDetailResponse;
import com.cms.protection.dto.FlagListResponse;
import com.cms.protection.dto.FlagResponse;
import com.cms.protection.dto.RecentActivityResponse;
import com.cms.protection.exception.FlagNotFoundException;
import com.cms.protection.exception.ProtectionExceptionHandler;
import com.cms.protection.exception.ProtectionForbiddenException;
import com.cms.protection.service.ClinicProtectionFlagService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #2): this module's first
 * contract tier. Web-layer only (mocked service), real JWT auth via a real token.
 */
@WebMvcTest(controllers = ClinicProtectionFlagController.class)
@Import({ProtectionExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class ClinicProtectionFlagControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private ClinicProtectionFlagService flagService;

    @Test
    void listReturnsOkWithTheFlagsPage() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        FlagResponse flag = new FlagResponse(
                UUID.randomUUID(), UUID.randomUUID(), "patient@example.com", "REPEATED_CANCELLATIONS",
                "4 cancellations in the last 30 days", Instant.now(), "OUTSTANDING", null, null);
        when(flagService.list(eq(accountId), eq(clinicId), any(), any(), anyInt(), anyInt()))
                .thenReturn(new FlagListResponse(List.of(flag), 0, 20, 1));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flags[0].signalType").value("REPEATED_CANCELLATIONS"))
                .andExpect(jsonPath("$.totalCount").value(1));
    }

    @Test
    void detailReturnsOkWithClinicScopedEvidenceAndTheGlobalLimitFact() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        FlagResponse flag = new FlagResponse(
                flagId, UUID.randomUUID(), "patient@example.com", "REPEATED_NO_SHOWS",
                "3 no-shows in the last 90 days", Instant.now(), "OUTSTANDING", null, null);
        RecentActivityResponse activity = new RecentActivityResponse(List.of(), List.of(), List.of(), List.of(), 15, true);
        when(flagService.detail(accountId, clinicId, flagId)).thenReturn(new FlagDetailResponse(flag, activity));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags/{flagId}", clinicId, flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag.signalType").value("REPEATED_NO_SHOWS"))
                .andExpect(jsonPath("$.recentActivity.atGlobalLimit").value(true))
                .andExpect(jsonPath("$.recentActivity.globalActiveAppointmentCount").value(15));
    }

    @Test
    void resolveReturnsOkWithTheUpdatedFlag() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        FlagResponse resolved = new FlagResponse(
                flagId, UUID.randomUUID(), "patient@example.com", "REPEATED_NO_SHOWS", "reason",
                Instant.now(), "RESOLVED", Instant.now(), accountId.toString());
        when(flagService.resolve(eq(accountId), eq(clinicId), eq(flagId), any())).thenReturn(resolved);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/protection/flags/{flagId}/resolve", clinicId, flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void returns403ForACallerWithoutClinicAdminAtThisClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(flagService.list(any(), any(), any(), any(), anyInt(), anyInt())).thenThrow(new ProtectionForbiddenException());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void returns404ForAFlagBelongingToADifferentClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID flagId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(flagService.detail(any(), any(), any())).thenThrow(new FlagNotFoundException(flagId));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags/{flagId}", clinicId, flagId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("FLAG_NOT_FOUND"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/flags", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
