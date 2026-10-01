package com.cms.booking.contract;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.PatientBookingController;
import com.cms.booking.api.PatientQueueBookingController;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.BookingLimitReachedException;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
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
 * 060-booking-abuse-prevention (contracts/booking-protection.md #1): the new
 * 409 BOOKING_LIMIT_REACHED response on both self-service booking-creation endpoints.
 */
@WebMvcTest(controllers = {PatientBookingController.class, PatientQueueBookingController.class})
@Import({BookingExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientBookingLimitContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PatientBookingService patientBookingService;

    @MockitoBean
    private PatientQueueBookingService patientQueueBookingService;

    @Test
    void fixedTimeBookingReturns409WhenTheLimitIsReached() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(patientBookingService.bookSlot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BookingLimitReachedException());

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_LIMIT_REACHED"));
    }

    @Test
    void queueBookingReturns409WhenTheLimitIsReached() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(patientQueueBookingService.bookSlot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new BookingLimitReachedException());

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("BOOKING_LIMIT_REACHED"));
    }
}
