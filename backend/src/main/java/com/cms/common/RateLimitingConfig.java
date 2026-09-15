package com.cms.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Throttles the app's sensitive public endpoints (login and public signup/registration) by
 * client IP - see {@link RateLimitingFilter}'s Javadoc for the full rationale and the "why not
 * a Spring Security filter" reasoning.
 */
@Configuration
public class RateLimitingConfig {

    @Value("${app.rate-limit.max-attempts:30}")
    private int maxAttempts;

    @Value("${app.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> staffLoginRateLimit() {
        return register("/api/v1/staff/login");
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> patientLoginRateLimit() {
        return register("/api/v1/patients/login");
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> patientSignupRateLimit() {
        return register("/api/v1/patients/signup");
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> clinicRegistrationRateLimit() {
        return register("/api/v1/clinics/register");
    }

    private FilterRegistrationBean<RateLimitingFilter> register(String path) {
        FilterRegistrationBean<RateLimitingFilter> registration =
                new FilterRegistrationBean<>(new RateLimitingFilter("POST", path, maxAttempts, windowSeconds));
        registration.addUrlPatterns(path);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
