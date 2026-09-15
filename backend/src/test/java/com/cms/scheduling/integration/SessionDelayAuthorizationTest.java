package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * _diagnostics [CRITICAL] - [full-repo-audit] - [CROSS_CLINIC_LEAK]: {@code SessionDelayController}
 * previously took no {@link org.springframework.security.core.Authentication} at all and never
 * checked the caller was staffed at the target clinic - any authenticated staff JWT from ANY
 * clinic could read another clinic's session delay. Regression coverage for the fix: an active
 * role at *some other* clinic must be rejected, and any active role at *this* clinic (not just
 * ClinicAdmin/Doctor - Operations too) must be allowed, matching "staff or the doctor may both
 * view" (FR-006).
 */
class SessionDelayAuthorizationTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions delay(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void aStaffMemberWithNoRoleAtThisClinicIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String unrelatedToken = unrelatedStaffToken();

        delay(clinic.getId().toString(), slot.getSession().getId().toString(), unrelatedToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void noTokenAtAllIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);

        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay",
                        clinic.getId(),
                        slot.getSession().getId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anOperationsStaffMemberAtThisClinicIsAllowed() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = operationsToken(clinic);

        delay(clinic.getId().toString(), slot.getSession().getId().toString(), token).andExpect(status().isOk());
    }
}
