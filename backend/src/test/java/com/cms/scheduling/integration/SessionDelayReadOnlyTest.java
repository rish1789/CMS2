package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

/** 026 US3 (P1, tied with US1), FR-005/SC-002: the read path never recomputes - two reads with no trigger in between return the identical stored value. */
class SessionDelayReadOnlyTest extends AbstractSessionDelayIntegrationTest {

    private MvcResult delay(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/delay", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andReturn();
    }

    @Test
    void twoReadsWithNoTriggerInBetweenReturnTheIdenticalStoredValue() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(45), SlotStatus.BOOKED);
        String clinicId = clinic.getId().toString();
        String sessionId = slot.getSession().getId().toString();
        String token = clinicAdminToken(clinic);

        // No trigger point has occurred yet - both reads must return the same "no outstanding
        // delay" state, regardless of the 45-minutes-past-due Slot sitting there unread.
        String firstRead = delay(clinicId, sessionId, token).getResponse().getContentAsString();
        Thread.sleep(50);
        String secondRead = delay(clinicId, sessionId, token).getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(secondRead).isEqualTo(firstRead);
    }
}
