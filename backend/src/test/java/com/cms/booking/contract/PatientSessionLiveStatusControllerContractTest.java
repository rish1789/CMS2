package com.cms.booking.contract;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.PatientSessionLiveStatusController;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.account.domain.Account;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.record.domain.Patient;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SessionLiveStatusService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 061-doctor-live-status (contracts/doctor-live-status.md): web-layer only, real JWT auth via a
 * real token - mirrors PatientBookingLimitContractTest's established shape for a patient-JWT-
 * gated endpoint in this module. BookingRepository/SessionLiveStatusService are mocked directly;
 * the calculation's own exhaustive branch coverage lives in SessionLiveStatusServiceTest, and the
 * booking-ownership-check pattern is the same one already proven by PatientQueuePositionController.
 */
@WebMvcTest(controllers = PatientSessionLiveStatusController.class)
@Import({
    BookingExceptionHandler.class,
    SecurityConfig.class,
    PatientAuthenticationEntryPoint.class,
    JwtService.class,
    PatientVisitOutcomesTestConfig.class
})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientSessionLiveStatusControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private SessionLiveStatusService sessionLiveStatusService;

    private Booking bookingOwnedBy(UUID patientAccountId, SlotStatus slotStatus) {
        PatientAccount account = mock(PatientAccount.class);
        when(account.getId()).thenReturn(patientAccountId);
        Patient patient = mock(Patient.class);
        when(patient.getPatientAccount()).thenReturn(account);

        Account doctorAccount = mock(Account.class);
        when(doctorAccount.getName()).thenReturn("Dr. Asha Rao");
        DoctorProfile doctorProfile = mock(DoctorProfile.class);
        when(doctorProfile.getAccount()).thenReturn(doctorAccount);
        Session session = mock(Session.class);
        when(session.getDoctorProfile()).thenReturn(doctorProfile);
        // 069: the patient's own outcome is derived from the booking, its slot and the session date.
        lenient().when(session.getSessionDate()).thenReturn(LocalDate.now());

        Slot slot = mock(Slot.class);
        when(slot.getSession()).thenReturn(session);
        when(slot.getStatus()).thenReturn(slotStatus);

        Booking booking = mock(Booking.class);
        when(booking.getPatient()).thenReturn(patient);
        when(booking.getSlot()).thenReturn(slot);
        lenient().when(booking.getStatus()).thenReturn(BookingStatus.ACTIVE);
        return booking;
    }

    @Test
    void aBookingThatIsNotTheCallersOwnReturns404() throws Exception {
        UUID bookingId = UUID.randomUUID();
        String token = jwtService.issueToken(UUID.randomUUID());
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("BOOKING_NOT_FOUND"));
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anActiveFixedTimeBookingReturnsTheFullPatientSafeShape() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        Booking booking = bookingOwnedBy(patientAccountId, SlotStatus.BOOKED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        SessionLiveStatusService.LiveStatus status =
                new SessionLiveStatusService.LiveStatus(true, SessionLiveStatusService.Status.DELAYED, 1, 3, 15, null, null);
        when(sessionLiveStatusService.liveStatusFor(booking.getSlot().getSession())).thenReturn(status);
        when(sessionLiveStatusService.estimatedWaitMinutesFor(booking.getSlot().getSession(), booking.getSlot(), status))
                .thenReturn(20);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(true))
                .andExpect(jsonPath("$.doctorName").value("Dr. Asha Rao"))
                .andExpect(jsonPath("$.currentPatientOrdinal").value(1))
                .andExpect(jsonPath("$.statusText").value("15 min delayed"))
                .andExpect(jsonPath("$.estimatedWaitMinutes").value(20))
                .andExpect(jsonPath("$.visitOutcome").value("SCHEDULED"));
    }

    @Test
    void aQueueModeBookingReturnsNotApplicableNotAnError() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        Booking booking = bookingOwnedBy(patientAccountId, SlotStatus.BOOKED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        when(sessionLiveStatusService.liveStatusFor(booking.getSlot().getSession()))
                .thenReturn(new SessionLiveStatusService.LiveStatus(false, null, null, null, null, null, null));

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicable").value(false))
                .andExpect(jsonPath("$.doctorName").doesNotExist());
    }

    @Test
    void anAlreadyResolvedSlotHasNoEstimatedWait() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        Booking booking = bookingOwnedBy(patientAccountId, SlotStatus.COMPLETED);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        SessionLiveStatusService.LiveStatus status =
                new SessionLiveStatusService.LiveStatus(true, SessionLiveStatusService.Status.COMPLETED, null, null, null, null, null);
        when(sessionLiveStatusService.liveStatusFor(booking.getSlot().getSession())).thenReturn(status);
        when(sessionLiveStatusService.estimatedWaitMinutesFor(booking.getSlot().getSession(), booking.getSlot(), status))
                .thenReturn(null);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusText").value("Visit complete"))
                .andExpect(jsonPath("$.estimatedWaitMinutes").doesNotExist());
    }

    /** 069 FR-004 (live-audit finding 1): a session that completed does not mean this patient was seen. */
    @Test
    void anOwnNoShowInACompletedSessionIsMissedNotVisitComplete() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID patientAccountId = UUID.randomUUID();
        String token = jwtService.issueToken(patientAccountId);
        Booking booking = bookingOwnedBy(patientAccountId, SlotStatus.NO_SHOW);
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        SessionLiveStatusService.LiveStatus status =
                new SessionLiveStatusService.LiveStatus(true, SessionLiveStatusService.Status.COMPLETED, null, null, null, null, null);
        when(sessionLiveStatusService.liveStatusFor(booking.getSlot().getSession())).thenReturn(status);

        mockMvc.perform(get("/api/v1/patients/bookings/{bookingId}/live-status", bookingId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusText").value("Missed appointment"))
                .andExpect(jsonPath("$.visitOutcome").value("NO_SHOW"));
    }
}
