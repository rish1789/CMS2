package com.cms.identity.staff.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.account.SecurityConfig;
import com.cms.identity.account.StaffAuthenticationEntryPoint;
import com.cms.identity.account.StaffJwtService;
import com.cms.identity.api.GlobalExceptionHandler;
import com.cms.identity.staff.StaffExceptionHandler;
import com.cms.identity.staff.StaffOnboardingController;
import com.cms.identity.staff.StaffOnboardingService;
import com.cms.identity.staff.dto.OnboardStaffResponse;
import java.util.UUID;
import org.hamcrest.Matchers;
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
 * T014: onboarding response schema contains no invitation/accept-link field (FR-010) and
 * no field that would indicate an email/SMS-send was attempted (FR-005) - notification
 * delivery is a not-yet-built feature (036/037), so there is no send-port to spy on yet;
 * this test instead pins the response shape so such a field can't be silently added.
 * Uses a mocked service (web layer only, real JWT auth via a real token) - no database.
 */
@WebMvcTest(controllers = StaffOnboardingController.class)
@Import({
    StaffExceptionHandler.class,
    GlobalExceptionHandler.class,
    SecurityConfig.class,
    StaffAuthenticationEntryPoint.class,
    StaffJwtService.class
})
@TestPropertySource(properties = "staff.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class StaffOnboardingContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffJwtService staffJwtService;

    @MockBean
    private StaffOnboardingService staffOnboardingService;

    @Test
    void onboardingResponseHasNoInvitationOrNotificationField() throws Exception {
        UUID clinicId = UUID.randomUUID();
        String token = staffJwtService.issueToken(UUID.randomUUID());

        when(staffOnboardingService.onboard(any(), eq(clinicId), any()))
                .thenReturn(new OnboardStaffResponse(
                        UUID.randomUUID(), "ops.hire@example.com", "OP-1234", "Tmp!Pass123", "Operations", null, false));

        mockMvc.perform(post("/api/v1/clinics/{clinicId}/staff", clinicId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                """
                                { "name": "Jamie Ops", "email": "ops.hire@example.com", "role": "Operations" }
                                """))
                .andExpect(status().isCreated())
                // No invitation/accept-link field anywhere (FR-010):
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("invit"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("acceptLink"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("inviteToken"))))
                // No field indicating an email/SMS send was attempted (FR-005):
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("notificationSent"))))
                .andExpect(content().string(Matchers.not(Matchers.containsStringIgnoringCase("emailSent"))));
    }
}
