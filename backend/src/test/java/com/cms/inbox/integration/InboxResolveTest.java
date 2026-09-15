package com.cms.inbox.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.AppointmentType;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.InboxItem;
import com.cms.scheduling.Session;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.ResultActions;

/** 038 US3, T032-T033: FR-011/FR-012 - only the claimant may resolve; a resolved item drops out of the outstanding list. */
class InboxResolveTest extends AbstractInboxIntegrationTest {

    private ResultActions claim(UUID clinicId, UUID itemId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/inbox/{itemId}/claim", clinicId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions resolve(UUID clinicId, UUID itemId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/inbox/{itemId}/resolve", clinicId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private InboxItem createWalkInItem(Clinic clinic, DoctorProfile doctor) {
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        insertWalkIn(operationsAccountId(clinic), clinic, session, appointmentType);
        return outstandingItemsOf(clinic).get(0);
    }

    @Test
    void claimantResolvesAndItemDropsFromOutstandingList() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);
        String claimantToken = operationsToken(clinic);
        claim(clinic.getId(), item.getId(), claimantToken).andExpect(status().isOk());

        resolve(clinic.getId(), item.getId(), claimantToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        assertThat(outstandingItemsOf(clinic)).isEmpty();
    }

    @Test
    void nonClaimantCannotResolve() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);
        claim(clinic.getId(), item.getId(), operationsToken(clinic)).andExpect(status().isOk());

        resolve(clinic.getId(), item.getId(), operationsToken(clinic))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_CLAIMANT"));
    }

    @Test
    void resolvingAnUnclaimedItemIsRejected() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);

        resolve(clinic.getId(), item.getId(), operationsToken(clinic))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_CLAIMANT"));
    }
}
