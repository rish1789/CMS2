package com.cms.identity.account.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.booking.api.BookingDetailController;
import com.cms.booking.api.DoctorBookingReadinessController;
import com.cms.booking.api.ScheduleDeletionController;
import com.cms.booking.api.SessionDeletionController;
import com.cms.booking.exception.BookingExceptionHandler;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.DoctorBookingReadinessService;
import com.cms.booking.service.ScheduleDeletionService;
import com.cms.booking.service.SessionDeletionService;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.api.ClinicRegistrationController;
import com.cms.identity.clinic.ClinicRegistrationService;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.identity.staff.api.StaffPasswordResetController;
import com.cms.identity.staff.service.StaffPasswordResetService;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.waitlist.api.StaffWaitlistController;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import com.cms.waitlist.service.WaitlistJoinService;
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
import org.springframework.test.web.servlet.RequestBuilder;

/**
 * 065-phase1-stabilization US1 (SEC-01 / BUG-001): the staff chain for {@code /api/v1/clinics/**}
 * must be fail-closed. Before 065 it ended in {@code anyRequest().permitAll()}, so the seven
 * endpoints below (never added to its allowlist) were reachable anonymously and failed with a 500
 * inside the controller instead of a 401 at the security boundary.
 */
@WebMvcTest(
        controllers = {
            BookingDetailController.class,
            DoctorBookingReadinessController.class,
            StaffWaitlistController.class,
            StaffPasswordResetController.class,
            SessionDeletionController.class,
            ScheduleDeletionController.class,
            ClinicRegistrationController.class
        })
@Import({BookingExceptionHandler.class, SecurityConfig.class, StaffAuthenticationEntryPoint.class, StaffJwtService.class})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class StaffChainFailClosedContractTest {

    private static final UUID CLINIC = UUID.randomUUID();
    private static final UUID ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockitoBean
    private BookingRepository bookingRepository;

    @MockitoBean
    private RoleAssignmentRepository roleAssignmentRepository;

    @MockitoBean
    private DoctorProfileRepository doctorProfileRepository;

    @MockitoBean
    private DoctorBookingReadinessService doctorBookingReadinessService;

    @MockitoBean
    private WaitlistJoinService waitlistJoinService;

    @MockitoBean
    private WaitlistEntryRepository waitlistEntryRepository;

    @MockitoBean
    private StaffPasswordResetService staffPasswordResetService;

    @MockitoBean
    private SessionRepository sessionRepository;

    @MockitoBean
    private SessionDeletionService sessionDeletionService;

    @MockitoBean
    private ScheduleDeletionService scheduleDeletionService;

    @MockitoBean
    private ClinicRegistrationService clinicRegistrationService;

    private void assertUnauthenticated(RequestBuilder request) throws Exception {
        mockMvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void bookingDetailRequiresAuthentication() throws Exception {
        assertUnauthenticated(get("/api/v1/clinics/{c}/bookings/{b}", CLINIC, ID));
    }

    @Test
    void doctorBookingReadinessRequiresAuthentication() throws Exception {
        assertUnauthenticated(get("/api/v1/clinics/{c}/doctors/booking-readiness", CLINIC));
    }

    @Test
    void waitlistCountRequiresAuthentication() throws Exception {
        assertUnauthenticated(get("/api/v1/clinics/{c}/waitlist/count", CLINIC));
    }

    @Test
    void staffPasswordResetRequiresAuthentication() throws Exception {
        assertUnauthenticated(post("/api/v1/clinics/{c}/staff/{a}/reset-password", CLINIC, ID));
    }

    @Test
    void staffSetPasswordRequiresAuthentication() throws Exception {
        assertUnauthenticated(post("/api/v1/clinics/{c}/staff/{a}/set-password", CLINIC, ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    void sessionDeletionRequiresAuthentication() throws Exception {
        assertUnauthenticated(delete("/api/v1/clinics/{c}/sessions/{s}", CLINIC, ID));
    }

    @Test
    void scheduleDeletionRequiresAuthentication() throws Exception {
        assertUnauthenticated(delete("/api/v1/clinics/{c}/doctors/{d}/schedules/{s}", CLINIC, ID, ID));
    }

    @Test
    void unmappedPathUnderTheStaffPrefixRequiresAuthentication() throws Exception {
        // The default itself must be closed: a future endpoint nobody remembered to list is protected.
        assertUnauthenticated(get("/api/v1/clinics/{c}/an-endpoint-that-does-not-exist", CLINIC));
    }

    @Test
    void invalidBearerTokenIsUnauthenticated() throws Exception {
        assertUnauthenticated(get("/api/v1/clinics/{c}/bookings/{b}", CLINIC, ID)
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"));
    }

    @Test
    void authenticatedCallerWithoutARoleAtTheClinicIsForbiddenNotUnauthenticated() throws Exception {
        // Authorization stays per-clinic in the controller/service layer (unchanged): a valid staff
        // token with no active role at this clinic gets 403, not 401.
        String token = staffJwtService.issueToken(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/clinics/{c}/bookings/{b}", CLINIC, ID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void clinicRegistrationStaysPublic() throws Exception {
        int status = mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
