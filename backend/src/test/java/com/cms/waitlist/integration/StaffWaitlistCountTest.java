package com.cms.waitlist.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/**
 * dashboard-live-data-2026-09-10: the clinic tools dashboard's "waitlist backlog" tile - how
 * many patients are currently WAITING (not OFFERED/CLAIMED/EXPIRED) at this clinic.
 */
class StaffWaitlistCountTest extends AbstractWaitlistIntegrationTest {

    @Test
    void countsOnlyWaitingEntries() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());
        saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var offeredSlot = slotRepository.findBySession_Id(session.getId()).get(0);
        // An OFFERED entry has already moved past "waiting" and must not count toward the backlog.
        saveOfferedWaitlistEntry(
                clinic, savePatientAccount(), doctor, null, offeredSlot, Instant.now(), Instant.now().plusSeconds(600));

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/waitlist/count", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + clinicAdminToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(2));
    }

    /** Unlike POST /waitlist (Operations-or-ClinicAdmin only), this read is open to any active role - a Doctor lands on the same dashboard. */
    @Test
    void aDoctorsOwnTokenIsAccepted() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic, "Cardiology");
        saveWaitlistEntry(clinic, savePatientAccount(), doctor, null, Instant.now());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/waitlist/count", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + doctorToken(doctor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingCount").value(1));
    }

    @Test
    void rejectsACallerWithNoActiveRoleAtTheClinic() throws Exception {
        Clinic clinic = saveClinic();

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/waitlist/count", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + unrelatedStaffToken()))
                .andExpect(status().isForbidden());
    }
}
