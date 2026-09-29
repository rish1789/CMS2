package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.domain.Booking;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 066 FR-003/SC-003: a first-ever fixed-time booking that fails after the Patient-record step leaves
 * no Patient record behind - the record commits or rolls back with the booking. Characterization
 * guard: it holds before and after 066, and fails if Patient creation is ever moved into its own
 * transaction.
 */
class PatientBookingFailureLeavesNoPatientTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void failedFirstBookingLeavesNoPatientRecordForTheAccount() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));

        // Another patient's active booking already occupies the slot, while the slot row itself still
        // reads OPEN - so the new booking passes the status check, links/creates its Patient record,
        // and only then fails on the booking INSERT (uq_booking_slot_active).
        var otherAccount = savePatientAccount();
        bookingRepository.saveAndFlush(Booking.bookedByPatient(
                slot, saveExistingPatient(clinic), appointmentType, new BigDecimal("300.00"), otherAccount.getId()));
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);

        var newAccount = savePatientAccount();
        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + patientToken(newAccount))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Brand New Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_ALREADY_BOOKED"));

        assertThat(patientRepository.findByClinic_IdAndPatientAccount_Id(clinic.getId(), newAccount.getId()))
                .isEmpty();
    }
}
