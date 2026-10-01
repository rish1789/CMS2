package com.cms.booking.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.ClinicBookingLimitOverrideController;
import com.cms.booking.dto.ClinicBookingLimitOverrideHistoryEntryResponse;
import com.cms.booking.dto.ClinicBookingLimitOverrideResponse;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.ClinicLimitExceedsGlobalCapException;
import com.cms.booking.exception.ClinicProtectionForbiddenException;
import com.cms.booking.service.ClinicBookingLimitOverrideService;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #3): ClinicAdmin-only, clinic-scoped. */
@WebMvcTest(controllers = ClinicBookingLimitOverrideController.class)
@Import({BookingExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class ClinicBookingLimitOverrideControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private ClinicBookingLimitOverrideService overrideService;

    @Test
    void getReturnsTheCurrentOverrideAndTheGlobalMax() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(overrideService.get(accountId, clinicId)).thenReturn(new ClinicBookingLimitOverrideResponse(5, 15));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/limit-override", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxActiveAppointments").value(5))
                .andExpect(jsonPath("$.globalMax").value(15));
    }

    @Test
    void putReturns200WithTheUpdatedOverride() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(overrideService.update(eq(accountId), eq(clinicId), eq(5), any()))
                .thenReturn(new ClinicBookingLimitOverrideResponse(5, 15));

        mockMvc.perform(put("/api/v1/clinics/{clinicId}/protection/limit-override", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxActiveAppointments\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maxActiveAppointments").value(5));
    }

    @Test
    void putReturns400WhenExceedingTheGlobalCap() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(overrideService.update(eq(accountId), eq(clinicId), eq(20), any()))
                .thenThrow(new ClinicLimitExceedsGlobalCapException(20, 15));

        mockMvc.perform(put("/api/v1/clinics/{clinicId}/protection/limit-override", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"maxActiveAppointments\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CLINIC_LIMIT_EXCEEDS_GLOBAL_CAP"));
    }

    @Test
    void deleteReturns200() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);

        mockMvc.perform(delete("/api/v1/clinics/{clinicId}/protection/limit-override", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void historyReturnsChangesNewestFirst() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(overrideService.history(accountId, clinicId))
                .thenReturn(List.of(new ClinicBookingLimitOverrideHistoryEntryResponse(5, 8, Instant.now(), accountId.toString())));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/limit-override/history", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newMaxActiveAppointments").value(8));
    }

    @Test
    void returns403ForACallerWithoutClinicAdminAtThisSpecificClinic() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(overrideService.get(accountId, clinicId)).thenThrow(new ClinicProtectionForbiddenException());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/limit-override", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/clinics/{clinicId}/protection/limit-override", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
