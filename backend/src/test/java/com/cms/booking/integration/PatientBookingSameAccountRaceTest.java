package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.cms.booking.domain.Booking;
import com.cms.protection.service.ProtectionSettingService;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 066 FR-007 (fixed-time path): with the booking limit switched off, 060's booking-limit row lock
 * is never taken, so two simultaneous first bookings by the same Patient Account (for different
 * slots) race on Patient-record creation - both must confirm and share one Patient record.
 */
class PatientBookingSameAccountRaceTest extends AbstractPatientBookingIntegrationTest {

    @Autowired
    private ProtectionSettingService protectionSettingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void disableBookingLimit() {
        protectionSettingService.update("booking-limit.enabled", "false", "test-066");
    }

    /** Runs before the base class cleanup (subclass @AfterEach first), restoring default settings. */
    @AfterEach
    void restoreDefaultSettings() {
        jdbcTemplate.update("DELETE FROM protection_setting_change_log");
        jdbcTemplate.update("DELETE FROM protection_setting");
    }

    @Test
    void concurrentFirstSlotBookingsBySameAccountBothConfirmWithOnePatientRecord() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        List<Slot> openSlots = slotRepository.findBySession_Id(session.getId()).stream()
                .filter(s -> s.getStatus() == SlotStatus.OPEN)
                .limit(2)
                .toList();
        assertThat(openSlots).hasSize(2);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var patientAccount = savePatientAccount();
        String token = patientToken(patientAccount);

        List<Callable<Integer>> bookings = openSlots.stream()
                .<Callable<Integer>>map(slot -> () -> mockMvc.perform(post(
                                        "/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book",
                                        clinic.getId(),
                                        slot.getId())
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "patientName": "Brand New Patient", "appointmentTypeId": "%s" }
                                        """
                                                .formatted(appointmentType.getId())))
                        .andReturn()
                        .getResponse()
                        .getStatus())
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = executor.invokeAll(bookings);
            for (Future<Integer> result : results) {
                assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(201);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(patientRepository.findAll()).hasSize(1);
        var patient = patientRepository.findAll().get(0);
        assertThat(patient.getPatientAccount().getId()).isEqualTo(patientAccount.getId());
        List<Booking> confirmed = bookingRepository.findAll();
        assertThat(confirmed).hasSize(2);
        assertThat(confirmed).allSatisfy(b -> assertThat(b.getPatient().getId()).isEqualTo(patient.getId()));
    }
}
