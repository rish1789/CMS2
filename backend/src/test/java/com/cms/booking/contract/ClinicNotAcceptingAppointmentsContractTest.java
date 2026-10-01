package com.cms.booking.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.PatientBookingCancellationController;
import com.cms.booking.api.PatientBookingController;
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingCancellationService;
import com.cms.booking.service.PatientBookingService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.scheduling.domain.ScheduleMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 062-rejected-clinic-gating (contract section 1 and 2, tasks.md T007): a booking at a rejected
 * clinic maps to 409 CLINIC_NOT_ACCEPTING_APPOINTMENTS, distinct from SLOT_ALREADY_BOOKED
 * (FR-002); and CLINIC_REJECTED is a system-only cancellation reason a patient can never submit.
 */
@WebMvcTest(controllers = {PatientBookingController.class, PatientBookingCancellationController.class})
@Import({
    BookingExceptionHandler.class,
    SecurityConfig.class,
    PatientAuthenticationEntryPoint.class,
    JwtService.class,
    PatientVisitOutcomesTestConfig.class
})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class ClinicNotAcceptingAppointmentsContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PatientBookingService patientBookingService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private BookingCancellationService bookingCancellationService;

    @Test
    void bookingAtARejectedClinicReturns409ClinicNotAcceptingAppointments() throws Exception {
        UUID clinicId = UUID.randomUUID();
        when(patientBookingService.bookSlot(any(), any(), any(), any()))
                .thenThrow(new ClinicNotAcceptingAppointmentsException(clinicId));

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinicId, UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueToken(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACCEPTING_APPOINTMENTS"))
                .andExpect(jsonPath("$.message").value("This clinic is not accepting appointments."));
    }

    @Test
    void aPatientCannotSubmitClinicRejectedAsTheirOwnCancellationReason() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Booking booking = mock(Booking.class, RETURNS_DEEP_STUBS);
        when(booking.getPatient().getPatientAccount().getId()).thenReturn(patientAccountId);
        // A Fixed-Time booking well past the cancellation cutoff, so the reason check is what decides.
        when(booking.getSlot().getSession().getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(booking.getSlot().getSession().getSessionDate()).thenReturn(LocalDate.now().plusDays(7));
        when(booking.getSlot().getStartTime()).thenReturn(LocalTime.of(10, 0));
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueToken(patientAccountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"CLINIC_REJECTED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_CANCELLATION_REASON"));
        verify(bookingCancellationService, never()).cancel(any(), any(), any());
    }
}
