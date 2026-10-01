package com.cms.scheduling.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.scheduling.api.SlotAppearedController;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.ForbiddenException;
import com.cms.scheduling.exception.ScheduleExceptionHandler;
import com.cms.scheduling.exception.SlotNotAppearableException;
import com.cms.scheduling.exception.SlotNotFoundException;
import com.cms.scheduling.service.SlotAppearedService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 057-day-sheet-status-overhaul (contracts/day-sheet-status-flow.md): web-layer only, real JWT
 * auth via a real token - mirrors TodaySessionStatsControllerContractTest's established shape
 * for a staff-JWT-gated endpoint in this module, with SlotAppearedService mocked directly
 * (its own exhaustive branch coverage lives in SlotAppearedServiceTest).
 */
@WebMvcTest(controllers = SlotAppearedController.class)
@Import({
    com.cms.support.AllowAllStaffSessionsTestConfig.class,ScheduleExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class SlotAppearedControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private SlotAppearedService slotAppearedService;

    @Test
    void markingABookedSlotAppearedReturns200() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        Slot slot = Mockito.mock(Slot.class);
        when(slot.getId()).thenReturn(slotId);
        when(slot.getStatus()).thenReturn(SlotStatus.APPEARED);
        when(slotAppearedService.markAppeared(accountId, clinicId, slotId)).thenReturn(slot);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPEARED"));
    }

    @Test
    void callerWithNoRoleAtTheClinicReturns403() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(slotAppearedService.markAppeared(any(), any(), any())).thenThrow(new ForbiddenException());

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void ineligibleSlotStatusReturns409() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(slotAppearedService.markAppeared(any(), any(), any())).thenThrow(new SlotNotAppearableException(slotId));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_APPEARABLE"));
    }

    @Test
    void unknownSlotReturns404() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        String token = staffJwtService.issueToken(accountId);
        when(slotAppearedService.markAppeared(any(), any(), any())).thenThrow(new SlotNotFoundException(slotId));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_FOUND"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/clinics/{clinicId}/slots/{slotId}/appeared", UUID.randomUUID(), UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
