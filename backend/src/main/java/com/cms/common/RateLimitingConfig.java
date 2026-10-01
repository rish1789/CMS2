package com.cms.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

/**
 * Throttles the app's sensitive public endpoints (login and public signup/registration) by
 * client IP - see {@link RateLimitingFilter}'s Javadoc for the full rationale and the "why not
 * a Spring Security filter" reasoning.
 */
@Configuration
public class RateLimitingConfig {

    static final String STAFF_LOGIN = "/api/v1/staff/login";
    static final String PATIENT_LOGIN = "/api/v1/patients/login";
    static final String PATIENT_SIGNUP = "/api/v1/patients/signup";
    static final String CLINIC_REGISTRATION = "/api/v1/clinics/register";

    @Value("${app.rate-limit.max-attempts:30}")
    private int maxAttempts;

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> staffLoginRateLimit() {
        return register(STAFF_LOGIN);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> patientLoginRateLimit() {
        return register(PATIENT_LOGIN);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> patientSignupRateLimit() {
        return register(PATIENT_SIGNUP);
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> clinicRegistrationRateLimit() {
        return register(CLINIC_REGISTRATION);
    }

    /**
     * 071-readable-rate-limit (live-audit finding 5): the limiter answers a throttled request itself,
     * before Spring Security's CORS step ever runs, so without this its 429 had no CORS headers and
     * the browser hid it from the page. Spring's own {@link CorsFilter}, built from the one shared
     * {@code corsConfigurationSource} policy, runs first on the throttled paths: allowed origins
     * get their headers (Spring Security's later CORS step skips a response that already has them),
     * disallowed origins are refused here as before, and preflights are answered here.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> throttledPathsCors(CorsConfigurationSource corsConfigurationSource) {
        FilterRegistrationBean<CorsFilter> registration =
                new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource));
        registration.addUrlPatterns(STAFF_LOGIN, PATIENT_LOGIN, PATIENT_SIGNUP, CLINIC_REGISTRATION);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    private FilterRegistrationBean<RateLimitingFilter> register(String path) {
        FilterRegistrationBean<RateLimitingFilter> registration =
                new FilterRegistrationBean<>(new RateLimitingFilter("POST", path, maxAttempts, windowSeconds));
        registration.addUrlPatterns(path);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
