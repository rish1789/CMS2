package com.cms.scheduling.api;

import com.cms.scheduling.service.SessionDelayService;
import com.cms.scheduling.service.SessionLiveStatusService;


import com.cms.identity.account.config.SecurityConfig;
import com.cms.scheduling.dto.SessionDelayResponse;
import com.cms.scheduling.dto.SessionLiveStatusResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * No role restriction beyond authentication - staff or the doctor may both view (FR-006).
 *
 * <p>_diagnostics [CRITICAL] - [full-repo-audit] - [CROSS_CLINIC_LEAK]: this endpoint took no
 * {@link Authentication} at all and never checked the caller was staffed at {@code clinicId} -
 * any authenticated staff JWT from ANY clinic could read another clinic's session delay by
 * supplying that clinic's real ids. "No role restriction" (above) meant no restriction on WHICH
 * role, not no restriction on WHICH clinic - fixed by requiring any active role assignment at
 * {@code clinicId}, mirroring {@code ClinicDoctorController}'s identical any-role gate.
 */
@RestController
public class SessionDelayController {

    private final SessionDelayService sessionDelayService;
    private final SessionLiveStatusService sessionLiveStatusService;

    public SessionDelayController(SessionDelayService sessionDelayService, SessionLiveStatusService sessionLiveStatusService) {
        this.sessionDelayService = sessionDelayService;
        this.sessionLiveStatusService = sessionLiveStatusService;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay")
    public SessionDelayResponse delay(
            @PathVariable UUID clinicId, @PathVariable UUID sessionId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        SessionDelayService.SessionDelay delay = sessionDelayService.currentDelay(callerAccountId, clinicId, sessionId);
        return new SessionDelayResponse(sessionId, delay.applicable(), delay.delayMinutes());
    }

    /** 061-doctor-live-status: sibling route, added alongside {@link #delay} - that route and its contract are unchanged. */
    @GetMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status")
    public SessionLiveStatusResponse liveStatus(
            @PathVariable UUID clinicId, @PathVariable UUID sessionId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        SessionLiveStatusService.LiveStatus status = sessionLiveStatusService.liveStatus(callerAccountId, clinicId, sessionId);
        return new SessionLiveStatusResponse(
                sessionId,
                status.applicable(),
                status.status() == null ? null : status.status().name(),
                status.currentPatientOrdinal(),
                status.expectedPatientOrdinal(),
                status.deviationMinutes(),
                status.firstSlotTime(),
                status.operationalDay());
    }
}
