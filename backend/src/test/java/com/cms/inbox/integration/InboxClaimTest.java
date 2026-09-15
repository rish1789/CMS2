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

/** 038 US2, T025-T026: FR-007/FR-008/FR-009 - claiming an unclaimed item, and rejecting a second claim. */
class InboxClaimTest extends AbstractInboxIntegrationTest {

    private ResultActions claim(UUID clinicId, UUID itemId, String token) throws Exception {
        return mockMvc.perform(post("/api/v1/clinics/{clinicId}/inbox/{itemId}/claim", clinicId, itemId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private InboxItem createWalkInItem(Clinic clinic, DoctorProfile doctor) {
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        insertWalkIn(operationsAccountId(clinic), clinic, session, appointmentType);
        return outstandingItemsOf(clinic).get(0);
    }

    @Test
    void claimingAnUnclaimedItemSucceeds() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);
        UUID claimant = operationsAccountId(clinic);

        claim(clinic.getId(), item.getId(), staffJwtService.issueToken(claimant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLAIMED"))
                .andExpect(jsonPath("$.claimedByAccountId").value(claimant.toString()));
    }

    @Test
    void secondClaimOnAnAlreadyClaimedItemIsRejected() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        InboxItem item = createWalkInItem(clinic, doctor);

        claim(clinic.getId(), item.getId(), operationsToken(clinic)).andExpect(status().isOk());

        claim(clinic.getId(), item.getId(), operationsToken(clinic))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALREADY_CLAIMED"));
    }

    @Test
    void claimingANonexistentItemIsNotFound() throws Exception {
        Clinic clinic = saveClinic();

        claim(clinic.getId(), UUID.randomUUID(), operationsToken(clinic))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("INBOX_ITEM_NOT_FOUND"));
    }
}
