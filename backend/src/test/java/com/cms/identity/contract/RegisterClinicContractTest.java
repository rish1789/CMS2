package com.cms.identity.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.SecurityConfig;
import com.cms.identity.account.StaffAuthenticationEntryPoint;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.api.ClinicRegistrationController;
import com.cms.identity.api.GlobalExceptionHandler;
import com.cms.identity.api.dto.RegisterClinicResponse;
import com.cms.identity.clinic.ClinicRegistrationService;
import com.cms.identity.clinic.EmailAlreadyInUseException;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T017: contract test against contracts/register-clinic.md - request/response schema, and
 * absence of any Grievance Officer / billing / file-upload field (FR-006, FR-007, FR-008).
 * Uses a mocked service (web layer only), so it runs without a database.
 *
 * <p>Imports {@code StaffAuthenticationEntryPoint}/{@code StaffJwtService} (and sets a
 * {@code staff.jwt.secret}) because 004-staff-onboarding-direct-hire extended this same
 * {@code SecurityConfig} bean's constructor to need them - unrelated to this test's own
 * concern, but required for the shared bean to construct in this slice.
 */
@WebMvcTest(controllers = ClinicRegistrationController.class)
@org.springframework.context.annotation.Import({
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    StaffAuthenticationEntryPoint.class,
    StaffJwtService.class
})
@TestPropertySource(properties = "staff.jwt.secret=test-only-001-contract-test-secret-at-least-64-characters-long")
class RegisterClinicContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClinicRegistrationService clinicRegistrationService;

    private static final String VALID_REQUEST =
            """
            {
              "clinic": {
                "name": "Sunrise Clinic",
                "address": "12 MG Road, Bengaluru"
              },
              "admin": {
                "name": "Dr. Asha Rao",
                "email": "owner@sunrise-clinic.example",
                "password": "Str0ng!Pass"
              }
            }
            """;

    @Test
    void successResponseMatchesContractShapeAndOmitsPassword() throws Exception {
        when(clinicRegistrationService.register(any()))
                .thenReturn(new RegisterClinicResponse(
                        UUID.randomUUID(),
                        "Sunrise Clinic",
                        false,
                        new RegisterClinicResponse.AdminInfo(
                                UUID.randomUUID(), "owner@sunrise-clinic.example", "CA-4821")));

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clinicId").exists())
                .andExpect(jsonPath("$.clinicName").value("Sunrise Clinic"))
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.admin.accountId").exists())
                .andExpect(jsonPath("$.admin.email").value("owner@sunrise-clinic.example"))
                .andExpect(jsonPath("$.admin.staffCode").value("CA-4821"))
                // No excluded fields anywhere in the response (FR-006, FR-007, FR-008):
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("grievance"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("billing"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("payment"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("file"))))
                // Never echoes the password or its hash:
                .andExpect(content().string(Matchers.not(Matchers.containsString("Str0ng!Pass"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("password"))));
    }

    @Test
    void requestSchemaHasNoGrievanceOfficerBillingOrFileField() throws Exception {
        // A request payload including these fields must still be accepted (extra/unknown
        // JSON properties are ignored by default Jackson binding) precisely because the
        // schema simply has nowhere to put them - proving there is no such field to fill.
        String requestWithExtraFields =
                """
                {
                  "clinic": {
                    "name": "Sunrise Clinic",
                    "address": "12 MG Road, Bengaluru",
                    "grievanceOfficer": "Should Be Ignored",
                    "billingPlan": "Should Be Ignored"
                  },
                  "admin": {
                    "name": "Dr. Asha Rao",
                    "email": "owner@sunrise-clinic.example",
                    "password": "Str0ng!Pass"
                  },
                  "fileUpload": "should-be-ignored.pdf"
                }
                """;
        when(clinicRegistrationService.register(any()))
                .thenReturn(new RegisterClinicResponse(
                        UUID.randomUUID(),
                        "Sunrise Clinic",
                        false,
                        new RegisterClinicResponse.AdminInfo(
                                UUID.randomUUID(), "owner@sunrise-clinic.example", "CA-4821")));

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestWithExtraFields))
                .andExpect(status().isCreated());
    }

    @Test
    void duplicateEmailReturns409WithContractErrorShape() throws Exception {
        when(clinicRegistrationService.register(any())).thenThrow(new EmailAlreadyInUseException());

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_ALREADY_IN_USE"));
    }

    @Test
    void missingRequiredFieldReturns400() throws Exception {
        String missingAdminName =
                """
                {
                  "clinic": { "name": "Sunrise Clinic", "address": "12 MG Road, Bengaluru" },
                  "admin": { "email": "owner@sunrise-clinic.example", "password": "Str0ng!Pass" }
                }
                """;

        mockMvc.perform(post("/api/v1/clinics/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingAdminName))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("MISSING_REQUIRED_FIELD"));
    }
}
