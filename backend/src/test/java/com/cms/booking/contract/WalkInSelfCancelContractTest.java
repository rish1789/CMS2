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
import java.time.LocalDate;
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
 * 063-front-desk-walk-in (contract section 4, tasks.md T006): a walk-in has no scheduled time, so the
 * patient self-service cutoff can't apply - and a walk-in is physically at the clinic, where staff
 * remove them from the line (FR-015). The patient cancel endpoint refuses it cleanly with 409
 * WALK_IN_NOT_SELF_CANCELLABLE instead of failing on the missing start time.
 */
@WebMvcTest(controllers = PatientBookingCancellationController.class)
@Import({BookingExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class WalkInSelfCancelContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private BookingCancellationService bookingCancellationService;

    @Test
    void aPatientCannotSelfCancelAWalkIn() throws Exception {
        UUID patientAccountId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        Booking walkIn = mock(Booking.class, RETURNS_DEEP_STUBS);
        when(walkIn.getPatient().getPatientAccount().getId()).thenReturn(patientAccountId);
        when(walkIn.getSlot().getSession().getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(walkIn.getSlot().getSession().getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
        when(walkIn.getSlot().getStartTime()).thenReturn(null);
        when(walkIn.getSlot().isUntimed()).thenReturn(true);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(walkIn));

        mockMvc.perform(post("/api/v1/patients/bookings/{bookingId}/cancel", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.issueToken(patientAccountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"FEELING_BETTER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("WALK_IN_NOT_SELF_CANCELLABLE"));
        verify(bookingCancellationService, never()).cancel(any(), any(), any());
    }
}
