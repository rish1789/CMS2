package com.cms.common.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 071-readable-rate-limit (live-audit finding 5): a throttled request from an allowed browser
 * origin must still carry the CORS headers, or the browser hides the 429 and the page only sees a
 * network failure. Runs through the real filter chain - CORS, the limiter and Spring Security -
 * with an isolated test limit of 3. Each test uses its own client address, because the limiter
 * keys on it and the context is shared within the class.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"app.rate-limit.max-attempts=3", "app.cors.allowed-origins=http://localhost:5173"})
class RateLimitCorsIntegrationTest {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withDatabaseName("cms_test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder throttledPost(String path, String clientAddress) {
        return post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
                .with(request -> {
                    request.setRemoteAddr(clientAddress);
                    return request;
                });
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"/api/v1/staff/login", "/api/v1/patients/login", "/api/v1/patients/signup", "/api/v1/clinics/register"})
    void aThrottledAllowedOriginRequestIsReadableByTheBrowser(String path) throws Exception {
        String client = "10.71.0." + Math.abs(path.hashCode() % 200);
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(throttledPost(path, client).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(status().is(Matchers.not(429)))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN));
        }

        mockMvc.perform(throttledPost(path, client).header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().is(429))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, Matchers.containsString("Retry-After")))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    /** No widening: a disallowed origin is still refused and never gets an allow-origin header. */
    @Test
    void aDisallowedOriginIsStillRefused() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(throttledPost("/api/v1/patients/signup", "10.71.1.1")
                            .header(HttpHeaders.ORIGIN, "http://evil.example"))
                    .andExpect(status().isForbidden())
                    .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        }
    }

    @Test
    void preflightStillWorksAndIsNotCounted() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(options("/api/v1/patients/signup")
                            .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type")
                            .with(request -> {
                                request.setRemoteAddr("10.71.2.1");
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, Matchers.containsString("POST")));
        }
        // Five preflights did not use up the limit: the first real request from that client is not throttled.
        mockMvc.perform(throttledPost("/api/v1/patients/signup", "10.71.2.1").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
                .andExpect(status().is(Matchers.not(429)));
    }

    /** Same-origin and non-browser callers send no Origin header; they are still throttled. */
    @Test
    void requestsWithoutAnOriginAreStillThrottled() throws Exception {
        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(throttledPost("/api/v1/patients/signup", "10.71.3.1"))
                    .andExpect(status().is(Matchers.not(429)));
        }
        mockMvc.perform(throttledPost("/api/v1/patients/signup", "10.71.3.1"))
                .andExpect(status().is(429))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
