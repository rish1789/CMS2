package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cms.booking.domain.Booking;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * 066 FR-007 (queue path): two simultaneous queue bookings by the same Patient Account at a clinic
 * where it has no Patient record yet both confirm and share one Patient record. The queue path is
 * not wrapped in one transaction (022), so 060's booking-limit lock never serializes it.
 */
class PatientQueueBookingSameAccountRaceTest extends AbstractQueueBookingIntegrationTest {

    @Test
    void concurrentFirstQueueBookingsBySameAccountBothConfirmWithOnePatientRecord() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        Callable<Integer> book = () -> mockMvc.perform(post(
                                "/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings",
                                clinic.getId(),
                                session.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "patientName": "Brand New Patient", "appointmentTypeId": "%s" }
                                """
                                        .formatted(appointmentType.getId())))
                .andReturn()
                .getResponse()
                .getStatus();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = executor.invokeAll(List.of(book, book));
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(patientRepository.findAll()).hasSize(1);
        var patient = patientRepository.findAll().get(0);
        assertThat(patient.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
        List<Booking> bookings = bookingRepository.findAll();
        assertThat(bookings).hasSize(2);
        assertThat(bookings).allSatisfy(b -> assertThat(b.getPatient().getId()).isEqualTo(patient.getId()));
    }
}
