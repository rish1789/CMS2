package com.cms.clinical.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 065-phase1-stabilization US5 (SEC-06): once the treating doctor's Doctor role at the clinic is
 * deactivated, they can no longer create clinical documentation there - but can still read what
 * they already wrote (historical visibility, spec 034 unchanged).
 */
class ConsultationNoteDeactivatedDoctorTest extends AbstractConsultationNoteIntegrationTest {

    @Test
    void deactivatedDoctorCannotCreateButCanStillReadTheirEarlierNote() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> slots = slotRepository.findBySession_Id(session.getId());
        Booking earlier = bookSlot(clinic, doctor, slots.get(0));
        Booking later = bookSlot(clinic, doctor, slots.get(1));
        String token = doctorToken(doctor);

        mockMvc.perform(post("/api/v1/clinics/{c}/bookings/{b}/consultation-notes", clinic.getId(), earlier.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Seen while still on staff.\"}"))
                .andExpect(status().isCreated());

        for (RoleAssignment role : roleAssignmentRepository.findByAccount_IdAndClinic_IdAndActiveTrue(
                doctor.getAccount().getId(), clinic.getId())) {
            role.deactivate(RoleAssignment.DeactivationReason.RESIGNED);
            roleAssignmentRepository.save(role);
        }

        mockMvc.perform(post("/api/v1/clinics/{c}/bookings/{b}/consultation-notes", clinic.getId(), later.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Written after leaving.\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(get("/api/v1/clinics/{c}/bookings/{b}/consultation-notes", clinic.getId(), earlier.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }
}
