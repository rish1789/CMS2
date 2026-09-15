package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 026 US1 (P1), FR-009 (Clarifications): only an active Operations staff member or ClinicAdmin may mark a Slot completed - never the Doctor. */
class SlotCompletionAuthorizationTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions complete(String clinicId, String slotId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/slots/{slotId}/complete", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void doctorCallerIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = doctorToken(doctor);

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void operationsCallerIsAllowed() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = operationsToken(clinic);

        complete(clinic.getId().toString(), slot.getId().toString(), token).andExpect(status().isOk());
    }

    @Test
    void clinicAdminCallerIsAllowed() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = clinicAdminToken(clinic);

        complete(clinic.getId().toString(), slot.getId().toString(), token).andExpect(status().isOk());
    }
}
