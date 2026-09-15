package com.cms.inbox.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.AppointmentType;
import com.cms.booking.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.record.PatientAnonymizationService;
import com.cms.scheduling.Session;
import com.cms.scheduling.SlotStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

/** 038 US1, T016/FR-016: an anonymized Patient's scrubbed name is reflected live, not frozen, in an existing WALK_IN Inbox Item. */
class InboxAnonymizationPropagationTest extends AbstractInboxIntegrationTest {

    @Autowired
    private PatientAnonymizationService patientAnonymizationService;

    @Test
    void anonymizedPatientNamePropagatesIntoExistingInboxItem() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        Booking booking = insertWalkIn(operationsAccountId(clinic), clinic, session, appointmentType);

        // Clear the "active future booking" precondition (037) without touching the Inbox Item
        // itself - only a claim/resolve action changes Inbox state, never a Booking cancellation.
        bookingRepository.cancelIfActive(booking.getId());
        booking.getSlot().setStatus(SlotStatus.OPEN);
        slotRepository.save(booking.getSlot());

        patientAnonymizationService.anonymize(clinic.getId(), booking.getPatient().getId());

        mockMvc.perform(get("/api/v1/clinics/{clinicId}/inbox", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + operationsToken(clinic)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].summary.patientName").value("Anonymized Patient"));
    }
}
