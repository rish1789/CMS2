package com.cms.booking.contract;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.PatientBookingController;
import com.cms.booking.api.PatientQueueBookingController;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.RateLimitedException;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #1): the new
 * 429 RATE_LIMITED response, including retryAfterSeconds, on both self-service booking-creation
 * endpoints.
 */
@WebMvcTest(controllers = {PatientBookingController.class, PatientQueueBookingController.class})
@Import({BookingExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientBookingRateLimitContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PatientBookingService patientBookingService;

    @MockBean
    private PatientQueueBookingService patientQueueBookingService;

    @Test
    void fixedTimeBookingReturns429WithRetryAfterSecondsWhenRateLimited() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(patientBookingService.bookSlot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RateLimitedException(900));

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/slots/{slotId}/book", clinicId, slotId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(900));
    }

    @Test
    void queueBookingReturns429WithRetryAfterSecondsWhenRateLimited() throws Exception {
        UUID clinicId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(patientQueueBookingService.bookSlot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new RateLimitedException(300));

        mockMvc.perform(post("/api/v1/patients/clinics/{clinicId}/sessions/{sessionId}/queue-bookings", clinicId, sessionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"patientName\":\"Asha\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(300));
    }
}
