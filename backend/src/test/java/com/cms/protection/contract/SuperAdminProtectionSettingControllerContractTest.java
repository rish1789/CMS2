package com.cms.protection.contract;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cms.identity.admin.config.SuperAdminAuthenticationEntryPoint;
import com.cms.identity.admin.config.SuperAdminJwtService;
import com.cms.identity.admin.config.SuperAdminSecurityConfig;
import com.cms.protection.api.SuperAdminProtectionSettingController;
import com.cms.protection.domain.ProtectionSettingChangeLog;
import com.cms.protection.exception.InvalidSettingValueException;
import com.cms.protection.exception.ProtectionExceptionHandler;
import com.cms.protection.exception.ProtectionSettingNotFoundException;
import com.cms.protection.exception.UnrecognizedSettingException;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #4): Super Admin realm, real JWT
 * auth via a real token - mirrors this codebase's other realm contract test shapes, adapted to
 * the Super Admin's username-based token.
 */
@WebMvcTest(controllers = SuperAdminProtectionSettingController.class)
@Import({
    ProtectionExceptionHandler.class,
    SuperAdminSecurityConfig.class,
    SuperAdminAuthenticationEntryPoint.class,
    SuperAdminJwtService.class,
    SuperAdminProtectionSettingControllerContractTest.PasswordEncoderTestConfig.class
})
@TestPropertySource(properties = "admin.super-admin.jwt.secret=test-only-contract-test-secret-at-least-64-characters-long-ok")
class SuperAdminProtectionSettingControllerContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SuperAdminJwtService superAdminJwtService;

    @MockitoBean
    private ProtectionSettingService protectionSettingService;

    /**
     * SuperAdminSecurityConfig's superAdminUserDetailsService bean needs a real PasswordEncoder
     * at context-startup time (normally provided app-wide by identity.account.config
     * .SecurityConfig, not imported here to keep this test's context minimal) - a plain
     * {@code @MockitoBean} returns null from {@code encode(...)}, which that bean setup can't
     * tolerate, so a real (if test-only) encoder is provided instead.
     */
    @TestConfiguration
    static class PasswordEncoderTestConfig {
        @Bean
        PasswordEncoder passwordEncoder() {
            return PasswordEncoderFactories.createDelegatingPasswordEncoder();
        }
    }

    @Test
    void listReturnsEveryNamedSettingWithIsDefaultReflectingWhetherARowExists() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        when(protectionSettingService.listAll())
                .thenReturn(List.of(
                        new ProtectionSettingService.SettingView("booking-limit.global-max-active", "15", true, null, null),
                        new ProtectionSettingService.SettingView(
                                "rate-limit.max-attempts", "5", false, Instant.now(), "super-admin")));

        mockMvc.perform(get("/api/v1/admin/protection-settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isDefault").value(true))
                .andExpect(jsonPath("$[1].isDefault").value(false));
    }

    @Test
    void updateReturnsTheUpdatedSetting() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        when(protectionSettingService.update(eq("booking-limit.global-max-active"), eq("20"), any()))
                .thenReturn(new ProtectionSettingService.SettingView(
                        "booking-limit.global-max-active", "20", false, Instant.now(), "super-admin"));

        mockMvc.perform(put("/api/v1/admin/protection-settings/booking-limit.global-max-active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"20\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("20"));
    }

    @Test
    void updateReturns400ForAnUnrecognizedName() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        when(protectionSettingService.update(eq("not-a-real-setting"), any(), any()))
                .thenThrow(new UnrecognizedSettingException("not-a-real-setting"));

        mockMvc.perform(put("/api/v1/admin/protection-settings/not-a-real-setting")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"5\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("UNRECOGNIZED_SETTING"));
    }

    @Test
    void updateReturns400ForAnOutOfRangeValue() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        when(protectionSettingService.update(eq("booking-limit.global-max-active"), eq("0"), any()))
                .thenThrow(new InvalidSettingValueException("booking-limit.global-max-active", "0"));

        mockMvc.perform(put("/api/v1/admin/protection-settings/booking-limit.global-max-active")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"0\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_SETTING_VALUE"));
    }

    @Test
    void historyReturnsTheChangesNewestFirst() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        ProtectionSettingChangeLog older = new ProtectionSettingChangeLog(
                "booking-limit.global-max-active", null, "15", Instant.now().minusSeconds(60), "super-admin");
        ProtectionSettingChangeLog newer = new ProtectionSettingChangeLog(
                "booking-limit.global-max-active", "15", "20", Instant.now(), "super-admin");
        when(protectionSettingService.history("booking-limit.global-max-active")).thenReturn(List.of(newer, older));

        mockMvc.perform(get("/api/v1/admin/protection-settings/booking-limit.global-max-active/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newValue").value("20"))
                .andExpect(jsonPath("$[1].newValue").value("15"));
    }

    @Test
    void historyReturns404ForAnUnrecognizedName() throws Exception {
        String token = superAdminJwtService.issueToken("super-admin");
        when(protectionSettingService.history("not-a-real-setting"))
                .thenThrow(new ProtectionSettingNotFoundException("not-a-real-setting"));

        mockMvc.perform(get("/api/v1/admin/protection-settings/not-a-real-setting/history")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/admin/protection-settings")).andExpect(status().isUnauthorized());
    }
}
