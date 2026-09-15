package com.cms.inbox.integration;

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

/** 038 US2, T028/Edge Cases: only the current claimant may release; a released item can be claimed by someone else. */
class InboxReleaseTest extends AbstractInboxIntegrationTest {

    private ResultActions claim(UUID clinicId, UUID itemId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/inbox/{itemId}/claim", clinicId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private ResultActions release(UUID clinicId, UUID itemId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/inbox/{itemId}/release", clinicId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private InboxItem createWalkInItem(Clinic clinic, DoctorProfile doctor) {
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        insertWalkIn(operationsAccountId(clinic), clinic, session, appointmentType);
        return outstandingItemsOf(clinic).get(0);
    }

    @Test
    void claimantCanReleaseAndSomeoneElseCanThenClaim() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);
        String claimantToken = operationsToken(clinic);

        claim(clinic.getId(), item.getId(), claimantToken).andExpect(status().isOk());

        release(clinic.getId(), item.getId(), claimantToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNCLAIMED"))
                .andExpect(jsonPath("$.claimedByAccountId").doesNotExist());

        claim(clinic.getId(), item.getId(), operationsToken(clinic)).andExpect(status().isOk());
    }

    @Test
    void nonClaimantCannotRelease() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);

        claim(clinic.getId(), item.getId(), operationsToken(clinic)).andExpect(status().isOk());

        release(clinic.getId(), item.getId(), operationsToken(clinic))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_CLAIMANT"));
    }
}
