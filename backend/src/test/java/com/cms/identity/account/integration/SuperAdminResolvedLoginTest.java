package com.cms.identity.account.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.admin.SuperAdminJwtService;
import com.cms.identity.staff.integration.AbstractStaffIntegrationTest;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 040-super-admin-rbac-login T010/US1: {@code POST /api/v1/staff/login} (the "Clinic
 * Portal") resolves the configured Super Admin credential before falling through to the
 * Account lookup, issuing a {@code SUPER_ADMIN}-audience JWT instead of a staff one.
 */
class SuperAdminResolvedLoginTest extends AbstractStaffIntegrationTest {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"token\"\\s*:\\s*\"([^\"]+)\"");

    @Autowired
    private SuperAdminJwtService superAdminJwtService;

    @Test
    void correctSuperAdminCredentialsResolveAsSuperAdmin() throws Exception {
        String body =
                """
                { "identifier": "%s", "password": "%s" }
                """
                        .formatted(SUPER_ADMIN_USERNAME, SUPER_ADMIN_PASSWORD);

        MvcResult result = mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.email").value(SUPER_ADMIN_USERNAME))
                .andExpect(jsonPath("$.role").value("SUPER_ADMIN"))
                .andReturn();

        String token = extractToken(result);
        Optional<String> username = superAdminJwtService.validateAndGetUsername(token);
        assertTrue(username.isPresent(), "expected the issued token to validate as a Super Admin JWT");
        assertEquals(SUPER_ADMIN_USERNAME, username.get());
    }

    @Test
    void wrongSuperAdminPasswordRejectedWithSameShapeAsUnknownStaffIdentifier() throws Exception {
        String wrongPasswordBody =
                """
                { "identifier": "%s", "password": "not-the-real-password" }
                """
                        .formatted(SUPER_ADMIN_USERNAME);
        String unknownStaffBody =
                """
                { "identifier": "does.not.exist@sunrise-clinic.example", "password": "Incorrect1!" }
                """;

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownStaffBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_CREDENTIALS"));
    }

    private static String extractToken(MvcResult result) throws Exception {
        Matcher matcher = TOKEN_PATTERN.matcher(result.getResponse().getContentAsString());
        assertTrue(matcher.find(), "expected a \"token\" field in the response body");
        return matcher.group(1);
    }
}
