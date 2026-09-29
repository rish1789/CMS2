package com.cms.scheduling.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
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

    /** 057-day-sheet-status-overhaul FR-006: the treating doctor gains access, but only from APPEARED - not BOOKED (see below). */
    @Test
    void treatingDoctorCompletesTheirOwnAppearedSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.APPEARED);
        String token = doctorToken(doctor);

        complete(clinic.getId().toString(), slot.getId().toString(), token).andExpect(status().isOk());
    }

    /** 057-day-sheet-status-overhaul: a doctor cannot complete a still-BOOKED slot - Appeared is the front-desk-owned gate for them. */
    @Test
    void treatingDoctorIsRejectedForAStillBookedSlot() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = doctorToken(doctor);

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_COMPLETABLE"));
    }

    /** 057-day-sheet-status-overhaul: no peer-doctor override, mirroring TreatingDoctorAuthorizationService's own precedent for clinical documentation. */
    @Test
    void nonTreatingDoctorIsForbiddenEvenWhenTheSlotIsAppeared() throws Exception {
        var clinic = saveClinic();
        var treatingDoctor = saveDoctorStaffedAt(clinic);
        var otherDoctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, treatingDoctor, LocalTime.now().minusMinutes(15), SlotStatus.APPEARED);
        String token = doctorToken(otherDoctor);

        complete(clinic.getId().toString(), slot.getId().toString(), token)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }
}
