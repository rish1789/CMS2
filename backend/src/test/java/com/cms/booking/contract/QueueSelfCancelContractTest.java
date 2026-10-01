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
import com.cms.booking.domain.Booking;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingCancellationService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.scheduling.domain.ScheduleMode;
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
 * 064-queue-send-in-complete (spec edge case "Patient self-cancel: unchanged", tasks.md T008): staff
 * can now cancel queue bookings, but a patient still can't self-cancel one - the patient endpoint's
 * own Fixed-Time check stays in place.
 */
@WebMvcTest(controllers = PatientBookingCancellationController.class)
@Import({BookingExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class QueueSelfCancelContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private BookingCancellationService bookingCancellationService;

    @Test
    void aPatientStillCannotSelfCancelAQueueBooking() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Booking queueBooking = mock(Booking.class, RETURNS_DEEP_STUBS);
        when(queueBooking.getPatient().getPatientAccount().getId()).thenReturn(patientAccountId);
        when(queueBooking.getSlot().getSession().getMode()).thenReturn(ScheduleMode.QUEUE);
        when(queueBooking.getSlot().getSession().getId()).thenReturn(UUID.randomUUID());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(queueBooking));

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueToken(patientAccountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"FEELING_BETTER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("NOT_A_FIXED_TIME_SESSION"));
        verify(bookingCancellationService, never()).cancel(any(), any(), any());
    }
}
