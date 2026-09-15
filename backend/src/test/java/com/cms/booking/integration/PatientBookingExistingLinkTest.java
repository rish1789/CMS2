package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.patient.record.Patient;
import com.cms.scheduling.SlotStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 021 FR-005/FR-006/FR-012, spec US1 AC2: booking as an already-linked patient succeeds and locks the resolved fee. */
class PatientBookingExistingLinkTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void bookingAsAnAlreadyLinkedPatientSucceedsAndLocksTheResolvedFeeAndDisappearsFromTheList() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        // Pre-links this Patient Account to a clinic-scoped Patient record via the same
        // service this feature itself calls, so this test exercises the "reuse existing
        // link" branch (FR-005) rather than the "create new" branch (021's other suite).
        Patient linked = patientRepository.save(new Patient(clinic, null, "Linked Patient", null));
        linked.setPatientAccount(patientAccount);
        patientRepository.save(linked);

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Linked Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lockedFee").value(300.00))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
                .andExpect(jsonPath("$.patientId").value(linked.getId().toString()));

        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(patientRepository.findAll()).hasSize(1); // no duplicate record created

        mockMvc.perform(get("/api/v1/patients/clinics/{clinicId}/slots", clinic.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slotId=='" + slot.getId() + "')]").doesNotExist());
    }
}
