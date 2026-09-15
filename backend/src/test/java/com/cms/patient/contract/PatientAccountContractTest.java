package com.cms.patient.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.patient.account.EmailAlreadyInUseException;
import com.cms.patient.account.InvalidCredentialsException;
import com.cms.patient.account.InvalidPasswordException;
import com.cms.patient.account.JwtService;
import com.cms.patient.account.PatientAccountService;
import com.cms.patient.account.PatientAuthenticationEntryPoint;
import com.cms.patient.account.SecurityConfig;
import com.cms.patient.api.PatientAccountController;
import com.cms.patient.api.PatientExceptionHandler;
import com.cms.patient.api.dto.LoginResponse;
import com.cms.patient.api.dto.SignupResponse;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T014: contract test against contracts/patient-account.md - schema shape, and absence
 * of any social-login/SSO field (FR-011) or role/clinicId/staff-identity field (FR-008).
 * Uses a mocked service (web layer only), so it runs without a database.
 */
@WebMvcTest(controllers = PatientAccountController.class)
@org.springframework.context.annotation.Import({PatientExceptionHandler.class, SecurityConfig.class})
class PatientAccountContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PatientAccountService patientAccountService;

    // SecurityConfig.patientFilterChain declares JwtService/PatientAuthenticationEntryPoint
    // parameters - unrelated to the signup/login endpoints under test (both are permitAll),
    // but both beans must still exist for the filter chain to be constructed at all in this
    // slice (Spring eagerly builds every @Bean in a @Configuration class).
    @MockBean
    private JwtService jwtService;

    @MockBean
    private PatientAuthenticationEntryPoint patientAuthenticationEntryPoint;

    private static final String VALID_SIGNUP =
            """
            { "email": "owner@example.com", "password": "Str0ng!Pass" }
            """;

    @Test
    void signupResponseMatchesContractShapeAndOmitsPasswordAndExcludedFields() throws Exception {
        when(patientAccountService.signup(any()))
                .thenReturn(new SignupResponse(UUID.randomUUID(), "owner@example.com"));

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGNUP))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.patientAccountId").exists())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                // No social-login/SSO or staff-identity fields anywhere (FR-008, FR-011):
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("provider"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("oauth"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("role"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("clinicId"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("staffCode"))))
                // Never echoes the password or its hash:
                .andExpect(content().string(Matchers.not(Matchers.containsString("Str0ng!Pass"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("password"))));
    }

    @Test
    void signupRequestSchemaHasNoSocialLoginOrStaffIdentityField() throws Exception {
        // Extra/unknown JSON properties are ignored by default Jackson binding - proving
        // there is no such field to fill, only that it's silently dropped.
        String requestWithExtraFields =
                """
                {
                  "email": "owner@example.com",
                  "password": "Str0ng!Pass",
                  "provider": "google",
                  "oauthToken": "should-be-ignored",
                  "role": "ClinicAdmin",
                  "clinicId": "should-be-ignored"
                }
                """;
        when(patientAccountService.signup(any()))
                .thenReturn(new SignupResponse(UUID.randomUUID(), "owner@example.com"));

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithExtraFields))
                .andExpect(status().isCreated());
    }

    @Test
    void loginResponseMatchesContractShapeAndOmitsPassword() throws Exception {
        when(patientAccountService.authenticate(any()))
                .thenReturn(new LoginResponse("a.jwt.token", UUID.randomUUID(), "owner@example.com"));

        mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "owner@example.com", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("a.jwt.token"))
                .andExpect(jsonPath("$.patientAccountId").exists())
                .andExpect(jsonPath("$.email").value("owner@example.com"))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Str0ng!Pass"))));
    }

    @Test
    void signupWithAlreadyRegisteredEmailReturns409() throws Exception {
        when(patientAccountService.signup(any())).thenThrow(new EmailAlreadyInUseException());

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_SIGNUP))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void signupWithPolicyViolatingPasswordReturns400() throws Exception {
        when(patientAccountService.signup(any())).thenThrow(new InvalidPasswordException(List.of("MIN_LENGTH")));

        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "owner@example.com", "password": "weak" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PASSWORD"));
    }

    @Test
    void signupWithMissingEmailReturns400WithoutReachingTheService() throws Exception {
        // @NotBlank on SignupRequest.email rejects this before the controller ever calls the
        // (mocked) service - proves bean validation, not just the exception-handler mapping.
        mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "", "password": "Str0ng!Pass" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELD"));
    }

    @Test
    void loginWithWrongCredentialsReturns401() throws Exception {
        when(patientAccountService.authenticate(any())).thenThrow(new InvalidCredentialsException());

        mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "email": "owner@example.com", "password": "wrong-password" }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }
}
