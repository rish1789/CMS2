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
 * 061-doctor-live-status (contracts/doctor-live-status.md, FR-012): the new `/live-status` route
 * reuses SessionDelayController's own scoping exactly - mirrors SessionDelayAuthorizationTest's
 * shape for the existing `/delay` route, plus the doctor-self-scoping case (a doctor-only caller
 * refused another doctor's session), which the existing test file doesn't need to cover since it
 * predates that pattern's application to this controller.
 */
class SessionLiveStatusAuthorizationTest extends AbstractSessionDelayIntegrationTest {

    private ResultActions liveStatus(String clinicId, String sessionId, String token) throws Exception {
        return mockMvc.perform(get("/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status", clinicId, sessionId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    void aStaffMemberWithNoRoleAtThisClinicIsForbidden() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String unrelatedToken = unrelatedStaffToken();

        liveStatus(clinic.getId().toString(), slot.getSession().getId().toString(), unrelatedToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void noTokenAtAllIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);

        mockMvc.perform(get(
                        "/api/v1/clinics/{clinicId}/sessions/{sessionId}/live-status",
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

        liveStatus(clinic.getId().toString(), slot.getSession().getId().toString(), token).andExpect(status().isOk());
    }

    @Test
    void aDoctorOnlyCallerIsRefusedAnotherDoctorsSession() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        DoctorProfile otherDoctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, treatingDoctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String otherDoctorToken = doctorToken(otherDoctor);

        liveStatus(clinic.getId().toString(), slot.getSession().getId().toString(), otherDoctorToken)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SESSION_NOT_FOUND"));
    }

    @Test
    void theTreatingDoctorSeesTheirOwnSession() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile treatingDoctor = saveDoctorStaffedAt(clinic);
        Slot slot = saveFixedTimeSlotAt(clinic, treatingDoctor, LocalTime.now().minusMinutes(15), SlotStatus.BOOKED);
        String token = doctorToken(treatingDoctor);

        liveStatus(clinic.getId().toString(), slot.getSession().getId().toString(), token).andExpect(status().isOk());
    }
}
