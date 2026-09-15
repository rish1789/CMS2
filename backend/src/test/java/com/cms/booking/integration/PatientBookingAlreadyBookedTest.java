package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.PatientBookingService;
import com.cms.booking.SlotAlreadyBookedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** 021 FR-002/FR-007/FR-009/FR-010, spec US1 AC3, edge cases: rejection paths, including a race-safety concurrency test. */
class PatientBookingAlreadyBookedTest extends AbstractPatientBookingIntegrationTest {

    private ResultActions book(UUID clinicId, UUID slotId, String token, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void bookingAnAlreadyBookedSlotIsRejected() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var accountA = savePatientAccount();
        var accountB = savePatientAccount();

        book(clinic.getId(), slot.getId(), patientToken(accountA),
                        """
                        { "patientName": "Patient A", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isCreated());

        book(clinic.getId(), slot.getId(), patientToken(accountB),
                        """
                        { "patientName": "Patient B", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SLOT_ALREADY_BOOKED"));

        assertThat(bookingRepository.findBySlot_Id(slot.getId())).isPresent();
    }

    @Test
    void concurrentBookingAttemptsAgainstOneSlotOnlyOneSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));

        List<UUID> patientAccountIds =
                IntStream.range(0, 10).mapToObj(i -> savePatientAccount().getId()).toList();

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> attempts = IntStream.range(0, 10)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            patientBookingService.bookSlot(
                                    patientAccountIds.get(i),
                                    clinic.getId(),
                                    slot.getId(),
                                    new PatientBookingService.BookSlotInput("Patient " + i, appointmentType.getId()));
                            return true;
                        } catch (SlotAlreadyBookedException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(attempts);
            long successCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successCount++;
                }
            }

            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void nonexistentSlotIdIsRejectedWithNotFound() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        book(clinic.getId(), UUID.randomUUID(), patientToken(patientAccount),
                        """
                        { "patientName": "Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_FOUND"));
    }

    @Test
    void slotBelongingToADifferentClinicIsRejectedWithNotFound() throws Exception {
        var clinicA = saveClinic();
        var clinicB = saveClinic();
        var doctor = saveDoctorStaffedAt(clinicA);
        var session = saveFixedTimeSessionWithSlots(clinicA, doctor);
        var slot = anOpenSlotOf(session);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        book(clinicB.getId(), slot.getId(), patientToken(patientAccount),
                        """
                        { "patientName": "Patient", "appointmentTypeId": "%s" }
                        """.formatted(appointmentType.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("SLOT_NOT_FOUND"));
    }

    @Test
    void appointmentTypeNotBelongingToTheSlotsDoctorIsRejectedWithNotFound() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var otherDoctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var slot = anOpenSlotOf(session);
        var otherDoctorsAppointmentType = saveAppointmentTypeWithOverride(otherDoctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();

        book(clinic.getId(), slot.getId(), patientToken(patientAccount),
                        """
                        { "patientName": "Patient", "appointmentTypeId": "%s" }
                        """.formatted(otherDoctorsAppointmentType.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("APPOINTMENT_TYPE_NOT_FOUND"));
    }
}
