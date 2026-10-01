package com.cms.identity.account.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.common.login.LoginAttemptExceptionHandler;
import com.cms.common.login.LoginTemporarilyLockedException;
import com.cms.identity.account.exception.InvalidCredentialsException;
import com.cms.identity.account.exception.NoActiveClinicAccessException;
import com.cms.support.AllowAllStaffSessionsTestConfig;
import com.cms.identity.account.exception.StaffClinicNotActiveException;
import com.cms.identity.account.config.SecurityConfig;
import com.cms.identity.account.api.StaffAuthController;
import com.cms.identity.account.service.StaffAuthService;
import com.cms.identity.account.config.StaffAuthenticationEntryPoint;
import com.cms.identity.account.config.StaffJwtService;
import com.cms.identity.account.dto.StaffLoginResponse;
import com.cms.identity.api.GlobalExceptionHandler;
import com.cms.identity.staff.exception.StaffExceptionHandler;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer ("contract") test for POST /api/v1/staff/login - real Spring Security filter chain,
 * real bean validation, with {@link StaffAuthService} (the extracted business logic - see
 * StaffAuthServiceTest for its own exhaustive branch coverage) mocked, so this runs without a
 * database. Covers the 200 success path and every flavor of 4xx failure this endpoint produces.
 */
@WebMvcTest(controllers = StaffAuthController.class)
@Import({
    StaffExceptionHandler.class,
    GlobalExceptionHandler.class,
    LoginAttemptExceptionHandler.class,
    SecurityConfig.class,
    AllowAllStaffSessionsTestConfig.class
})
class StaffAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StaffAuthService staffAuthService;

    // Not exercised by this endpoint directly, but SecurityConfig's OTHER filter chain
    // (/api/v1/clinics/**) depends on both, and Spring eagerly constructs every @Bean in a
    // @Configuration class - these just need to exist to satisfy that wiring.
    @MockitoBean
    private StaffJwtService staffJwtService;

    @MockitoBean
    private StaffAuthenticationEntryPoint staffAuthenticationEntryPoint;

    @Test
    void correctCredentialsReturn200WithAToken() throws Exception {
        UUID accountId = UUID.randomUUID();
        when(staffAuthService.login(any()))
                .thenReturn(new StaffLoginResponse("a.jwt.token", accountId, "staff@example.com", "STAFF"));

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "staff@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("a.jwt.token"))
                .andExpect(jsonPath("$.role").value("STAFF"));
    }

    /** 075-login-hardening (D-3C-2): one generic answer for a wrong password and an unknown identifier alike. */
    @Test
    void invalidCredentialsReturn401WithTheGenericError() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new InvalidCredentialsException("Incorrect email, staff code or password."));

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "staff@example.com", "password": "wrong-password" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("Incorrect email, staff code or password."));
    }

    @Test
    void aLockedIdentifierReturns429WithRetryAfter() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new LoginTemporarilyLockedException(840));

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "nobody@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "840"))
                .andExpect(jsonPath("$.error").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.retryAfterSeconds").value(840));
    }

    @Test
    void anAccountWithNoActiveClinicReturns403() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new NoActiveClinicAccessException());

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "staff@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("NO_ACTIVE_CLINIC_ACCESS"));
    }

    @Test
    void blankIdentifierReturns400WithoutReachingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELD"));
    }

    // 062-rejected-clinic-gating (FR-007, contract section 3, tasks.md T024)
    @Test
    void aStaffMemberWhoseOnlyClinicIsRejectedGets403ClinicNotActive() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new StaffClinicNotActiveException());

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"doctor@example.com\",\"password\":\"Str0ng!Pass\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("CLINIC_NOT_ACTIVE"))
                .andExpect(jsonPath("$.message")
                        .value("Your clinic is not currently active. Contact your clinic administrator."));
    }
}
