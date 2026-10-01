package com.cms.booking.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.FrontDeskWalkInController;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.VisitReason;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.exception.DuplicateWalkInException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidEmailException;
import com.cms.booking.exception.PatientRequiredException;
import com.cms.booking.exception.VisitReasonDetailRequiredException;
import com.cms.booking.exception.VisitReasonRequiredException;
import com.cms.booking.service.FrontDeskWalkInService;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.scheduling.domain.ScheduleMode;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 063-front-desk-walk-in (contract section 1, tasks.md T015): {@code POST
 * /api/v1/clinics/{clinicId}/walk-ins} - 201 with the documented placement body, each registration
 * error mapped to its documented status, and 401 without a staff token.
 */
@WebMvcTest(controllers = FrontDeskWalkInController.class)
@Import({BookingExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class FrontDeskWalkInControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private FrontDeskWalkInService frontDeskWalkInService;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    private String body() {
        return "{\"sessionId\":\"" + UUID.randomUUID() + "\",\"patientName\":\"Asha Rao\",\"patientPhone\":\"9876543210\","
                + "\"patientEmail\":\"asha@example.com\",\"appointmentTypeId\":\"" + UUID.randomUUID() + "\","
                + "\"visitReason\":\"PAIN\",\"visitReasonDetail\":null,\"confirmDuplicate\":false}";
    }

    @Test
    void registeringAFixedTimeWalkInReturns201WithThePlacement() throws Exception {
        Booking booking = mock(Booking.class, RETURNS_DEEP_STUBS);
        UUID bookingId = UUID.randomUUID();
        when(booking.getId()).thenReturn(bookingId);
        when(booking.getSlot().getTokenNumber()).thenReturn(1);
        when(booking.getSlot().getSession().getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(booking.getSlot().getSession().getDoctorProfile().getAccount().getName()).thenReturn("Dr. Rao");
        when(booking.getPatient().getName()).thenReturn("Asha Rao");
        when(booking.getLockedFee()).thenReturn(new BigDecimal("450.00"));
        when(booking.getVisitReason()).thenReturn(VisitReason.PAIN);
        when(frontDeskWalkInService.register(eq(accountId), eq(clinicId), any()))
                .thenReturn(new FrontDeskWalkInService.Registration(booking, 1));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bookingId").value(bookingId.toString()))
                .andExpect(jsonPath("$.mode").value("FIXED_TIME"))
                .andExpect(jsonPath("$.tokenNumber").value(1))
                .andExpect(jsonPath("$.walkInPosition").value(1))
                .andExpect(jsonPath("$.patientName").value("Asha Rao"))
                .andExpect(jsonPath("$.doctorName").value("Dr. Rao"))
                .andExpect(jsonPath("$.lockedFee").value(450.00))
                .andExpect(jsonPath("$.visitReason").value("PAIN"));
    }

    static Stream<Arguments> errors() {
        return Stream.of(
                Arguments.of(new VisitReasonRequiredException(), 400, "VISIT_REASON_REQUIRED"),
                Arguments.of(new VisitReasonDetailRequiredException(), 400, "VISIT_REASON_DETAIL_REQUIRED"),
                Arguments.of(new PatientRequiredException(), 400, "PATIENT_REQUIRED"),
                Arguments.of(new InvalidEmailException(), 400, "INVALID_EMAIL"),
                Arguments.of(new DuplicateWalkInException(), 409, "DUPLICATE_WALK_IN"),
                Arguments.of(new ForbiddenException(), 403, "FORBIDDEN"));
    }

    @ParameterizedTest(name = "{2} -> {1}")
    @MethodSource("errors")
    void eachRegistrationErrorMapsToItsDocumentedStatus(RuntimeException error, int httpStatus, String code)
            throws Exception {
        when(frontDeskWalkInService.register(any(), any(), any())).thenThrow(error);

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + staffJwtService.issueToken(accountId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().is(httpStatus))
                .andExpect(jsonPath("$.error").value(code));
    }

    @Test
    void registeringWithoutAStaffTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/clinics/{clinicId}/walk-ins", clinicId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body()))
                .andExpect(status().isUnauthorized());
    }
}
