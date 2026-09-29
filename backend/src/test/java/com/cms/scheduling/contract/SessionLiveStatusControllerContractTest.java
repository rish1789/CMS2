package com.cms.scheduling.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.scheduling.api.SessionDelayController;
import com.cms.scheduling.exception.ScheduleExceptionHandler;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.service.SessionDelayService;
import com.cms.scheduling.service.SessionLiveStatusService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 061-doctor-live-status (contracts/doctor-live-status.md): web-layer only, real JWT auth via a
 * real token - mirrors SlotAppearedControllerContractTest's established shape for a staff-JWT-
 * gated endpoint in this module. SessionLiveStatusService is mocked directly; its own exhaustive
 * branch coverage lives in SessionLiveStatusServiceTest.
 */
@WebMvcTest(controllers = SessionDelayController.class)
@Import({ScheduleExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class SessionLiveStatusControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockBean
    private SessionDelayService sessionDelayService;

    @MockBean
    private SessionLiveStatusService sessionLiveStatusService;

    @Test
    void aFixedTimeSessionReturnsTheFullLiveStatusShape() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(sessionLiveStatusService.liveStatus(accountId, clinicId, sessionId))
                .thenReturn(new SessionLiveStatusService.LiveStatus(
                        true,
                        SessionLiveStatusService.Status.DELAYED,
                        1,
                        3,
                        15,
                        LocalTime.of(9, 0),
                        LocalDate.of(2026, 9, 23)));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.currentPatientOrdinal").value(1))
                .andExpect(jsonPath("$.expectedPatientOrdinal").value(3))
                .andExpect(jsonPath("$.deviationMinutes").value(15))
                .andExpect(jsonPath("$.firstSlotTime").value("09:00:00"))
                .andExpect(jsonPath("$.operationalDay").value("2026-09-23"));
    }

    @Test
    void aQueueModeSessionReturnsNotApplicableNotAnError() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(sessionLiveStatusService.liveStatus(accountId, clinicId, sessionId))
                .thenReturn(new SessionLiveStatusService.LiveStatus(false, null, null, null, null, null, null));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.status").doesNotExist());
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(
                        get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aDoctorRequestingAnotherDoctorsSessionReturns404() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(sessionLiveStatusService.liveStatus(any(), any(), any())).thenThrow(new SessionNotFoundException(sessionId));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}
