package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.PatientBookingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/** 021 FR-004, spec US2 AC1-AC2: a first-ever booking at a clinic auto-creates or auto-links a Patient record. */
class PatientBookingFirstTimeLinkTest extends AbstractPatientBookingIntegrationTest {

    @Test
    void firstBookingWithNoMatchCreatesAndLinksANewPatientRecord() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Brand New Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated());

        assertThat(patientRepository.findAll()).hasSize(1);
        var created = patientRepository.findAll().get(0);
        assertThat(created.getName()).isEqualTo("Brand New Patient");
        assertThat(created.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
        assertThat(created.getClinic().getId()).isEqualTo(clinic.getId());
    }

    @Test
    void firstBookingWithAPhoneMatchLinksTheExistingWalkInRecordInsteadOfDuplicating() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        String phone = aValidMobileNumber();
        var walkIn = saveWalkInPatientWithPhone(clinic, phone);
        var patientAccount = savePatientAccount(phone);
        String token = patientToken(patientAccount);

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinic.getId(), slot.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Ignored - Matched By Phone", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientId").value(walkIn.getId().toString()));

        assertThat(patientRepository.findAll()).hasSize(1); // no duplicate created
        var linked = patientRepository.findById(walkIn.getId()).orElseThrow();
        assertThat(linked.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
    }

    /** 021 convergence T024: closes the same race class already proven in PatientBookingAlreadyBookedTest, but for concurrent first-time Patient-record *creation* (uq_patient_clinic_account) rather than Slot booking. */
    @Test
    void concurrentFirstBookingsForTheSamePatientAccountResultInExactlyOnePatientRecord() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<com.cms.scheduling.Slot> slots = slotRepository.findBySession_Id(session.getId());
        var slotA = slots.get(0);
        var slotB = slots.get(1);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> attempts = List.of(
                    () -> {
                        patientBookingService.bookSlot(
                                patientAccount.getId(),
                                clinic.getId(),
                                slotA.getId(),
                                new PatientBookingService.BookSlotInput("Racer A", appointmentType.getId()));
                        return true;
                    },
                    () -> {
                        patientBookingService.bookSlot(
                                patientAccount.getId(),
                                clinic.getId(),
                                slotB.getId(),
                                new PatientBookingService.BookSlotInput("Racer B", appointmentType.getId()));
                        return true;
                    });

            List<Future<Boolean>> futures = executor.invokeAll(attempts);
            for (Future<Boolean> f : futures) {
                assertThat(f.get()).isTrue(); // both bookings succeed - no false SLOT_ALREADY_BOOKED
            }
        } finally {
            executor.shutdown();
        }

        assertThat(patientRepository.findAll()).hasSize(1); // exactly one Patient record, not two
        assertThat(bookingRepository.findAll()).hasSize(2); // both bookings created, under the same Patient
        var patient = patientRepository.findAll().get(0);
        bookingRepository.findAll().forEach(b -> assertThat(b.getPatient().getId()).isEqualTo(patient.getId()));
    }
}
