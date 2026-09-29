package com.cms.patient.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.patient.account.config.JwtService;
import com.cms.patient.account.config.PatientAuthenticationEntryPoint;
import com.cms.patient.account.config.SecurityConfig;
import com.cms.patient.account.service.PatientAccountService;
import com.cms.patient.api.PatientAccountController;
import com.cms.patient.api.PatientExceptionHandler;
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
 * 065-phase1-stabilization US1 (SEC-01): the patient chain for {@code /api/v1/patients/**} must be
 * fail-closed - only signup and login are public. Before 065 any path missing from its allowlist
 * fell through to {@code anyRequest().permitAll()}.
 */
@WebMvcTest(controllers = PatientAccountController.class)
@Import({PatientExceptionHandler.class, SecurityConfig.class, PatientAuthenticationEntryPoint.class, JwtService.class})
@TestPropertySource(properties = "patient.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class PatientChainFailClosedContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private PatientAccountService patientAccountService;

    @Test
    void unmappedPathUnderThePatientPrefixRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/patients/an-endpoint-that-does-not-exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedPatientPassesTheBoundary() throws Exception {
        String token = jwtService.issueToken(UUID.randomUUID());

        mockMvc.perform(get("/api/v1/patients/an-endpoint-that-does-not-exist")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void signupStaysPublic() throws Exception {
        int status = mockMvc.perform(post("/api/v1/patients/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }

    @Test
    void loginStaysPublic() throws Exception {
        int status = mockMvc.perform(post("/api/v1/patients/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn()
                .getResponse()
                .getStatus();

        assertThat(status).isNotIn(401, 403);
    }
}
