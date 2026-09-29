package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 061-doctor-live-status (spec.md Testing Strategy): a real session walked through
 * Not started -> Delayed -> catches up -> On time -> Running early against a real database, via
 * real Slot-status transitions - not just the unit-level pointer calculation.
 *
 * <p>Written/compiled per this project's standing convention; unexecuted in this sandbox
 * (Testcontainers/Docker limitation), same as every other integration tier test here.
 */
class SessionLiveStatusFullLifecycleTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions liveStatus(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void aSessionProgressesThroughEveryLiveStatusAsItsSlotsAreResolved() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        String token = clinicAdminToken(clinic);
        LocalTime now = LocalTime.now();

        // Not started: a single slot, 30 minutes in the future, still Open.
        Slot slot1 = saveFixedTimeSlotAt(clinic, doctor, now.plusMinutes(30), SlotStatus.BOOKED);
        var session = slot1.getSession();
        String clinicId = clinic.getId().toString();
        String sessionId = session.getId().toString();

        // Rewind slot1 to a scheduled time already in the past so "now" (real clock) has passed it,
        // and add two more slots - one already past, one not yet due - to exercise every pointer.
        slot1 = updateSlotTime(slot1, now.minusMinutes(30), SlotStatus.BOOKED);
        Slot slot2 = addSlotAt(session, now.minusMinutes(15), SlotStatus.BOOKED);
        Slot slot3 = addSlotAt(session, now, SlotStatus.BOOKED);

        // Doctor hasn't touched anything yet - still on slot1, but slot3's time has arrived -> Delayed.
        liveStatus(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELAYED"))
                .andExpect(jsonPath("$.currentPatientOrdinal").value(1))
                .andExpect(jsonPath("$.expectedPatientOrdinal").value(3));

        // Doctor catches up: completes slot1 and slot2.
        slot1.setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slot1);
        slot2.setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slot2);

        liveStatus(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_TIME"))
                .andExpect(jsonPath("$.currentPatientOrdinal").value(3))
                .andExpect(jsonPath("$.expectedPatientOrdinal").value(3));

        // Doctor finishes slot3 too, and a 4th slot (not yet due) is added - now running ahead.
        slot3.setStatus(SlotStatus.COMPLETED);
        slotRepository.save(slot3);
        addSlotAt(session, now.plusMinutes(15), SlotStatus.BOOKED);

        liveStatus(clinicId, sessionId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RUNNING_EARLY"))
                .andExpect(jsonPath("$.currentPatientOrdinal").value(4))
                .andExpect(jsonPath("$.expectedPatientOrdinal").value(3));
    }

    private Slot updateSlotTime(Slot slot, LocalTime newStartTime, SlotStatus status) {
        // Slot's scheduled time is set at construction and has no setter - the fixture instead
        // deletes and recreates it at the new time, matching addSlotAt's own construction shape.
        slotRepository.delete(slot);
        Slot replacement = new Slot(slot.getSession(), newStartTime, newStartTime.plusMinutes(15));
        replacement.setStatus(status);
        return slotRepository.save(replacement);
    }
}
