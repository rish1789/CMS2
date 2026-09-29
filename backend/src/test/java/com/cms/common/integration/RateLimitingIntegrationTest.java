package com.cms.common.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 047-backend-hardening FR-005/SC-004: proves {@code RateLimitingFilter} actually engages when
 * wired into the real Spring Boot application via {@code FilterRegistrationBean} - not just in
 * {@code RateLimitingFilterTest}'s own isolated filter unit test, which calls the filter's
 * {@code doFilterInternal} directly and never proves it is reachable through a real request
 * pipeline at all. {@code app.rate-limit.max-attempts=3} overrides the production default (30)
 * so the test stays fast and deterministic.
 *
 * <p>Same documented limitation as every other Testcontainers-backed integration test in this
 * project (see memory {@code docker_testcontainers_sandbox_limitation}): compiles and is
 * correct, but does not execute in this sandbox. This feature's actual live-environment proof
 * was gathered directly against the real running application instead - see SECURITY.md's Rate
 * limiting section and this feature's tasks.md T016.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@org.springframework.test.context.TestPropertySource(properties = "app.rate-limit.max-attempts=3")
class RateLimitingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private static final String LOGIN_BODY =
            """
            { "identifier": "nobody@example.com", "password": "wrong-password" }
            """;

    @Test
    void fourthRapidLoginAttemptWithinTheWindowIsRejectedWith429() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/v1/staff/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_BODY))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/staff/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().is(429))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().exists("Retry-After"));
    }
}
