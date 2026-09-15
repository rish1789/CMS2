package com.cms.scheduling;

import com.cms.identity.account.SecurityConfig;
import com.cms.scheduling.dto.SessionDelayResponse;
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

    public SessionDelayController(SessionDelayService sessionDelayService) {
        this.sessionDelayService = sessionDelayService;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay")
    public SessionDelayResponse delay(
            @PathVariable UUID clinicId, @PathVariable UUID sessionId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        SessionDelayService.SessionDelay delay = sessionDelayService.currentDelay(callerAccountId, clinicId, sessionId);
        return new SessionDelayResponse(sessionId, delay.applicable(), delay.delayMinutes());
    }
}
