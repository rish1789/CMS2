package com.cms.booking.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Session;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 029 US1 (P1): Operations-or-ClinicAdmin-only write-action gate, clinic-scoping. */
class SessionCancellationAccessTest extends AbstractSessionCancellationIntegrationTest {

    private ResultActions cancel(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/sessions/{sessionId}/cancel", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void staffWithNoRoleAtAllAtThisClinicIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0));

        cancel(clinic.getId().toString(), session.getId().toString(), unrelatedStaffToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void doctorsOwnTokenIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0));

        cancel(clinic.getId().toString(), session.getId().toString(), doctorToken(doctor))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void sessionAtADifferentClinicIsNotFound() throws Exception {
        var clinic = saveClinic();
        var otherClinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        bookSlot(clinic, doctor, slots.get(0));

        cancel(otherClinic.getId().toString(), session.getId().toString(), clinicAdminToken(otherClinic))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }
}
