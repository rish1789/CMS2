package com.cms.identity.account.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.exception.AccountNotFoundException;
import com.cms.identity.account.exception.IncorrectPasswordException;
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
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
@Import({StaffExceptionHandler.class, GlobalExceptionHandler.class, SecurityConfig.class})
class StaffAuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StaffAuthService staffAuthService;

    // Not exercised by this endpoint directly, but SecurityConfig's OTHER filter chain
    // (/api/v1/clinics/**) depends on both, and Spring eagerly constructs every @Bean in a
    // @Configuration class - these just need to exist to satisfy that wiring.
    @MockBean
    private StaffJwtService staffJwtService;

    @MockBean
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

    @Test
    void wrongPasswordReturns401WithIncorrectPasswordError() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new IncorrectPasswordException());

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "staff@example.com", "password": "wrong-password" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INCORRECT_PASSWORD"));
    }

    @Test
    void unknownIdentifierReturns401WithAccountNotFoundError() throws Exception {
        when(staffAuthService.login(any())).thenThrow(new AccountNotFoundException());

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "identifier": "nobody@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_FOUND"));
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
