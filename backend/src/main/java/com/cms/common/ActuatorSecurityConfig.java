package com.cms.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 045-backend-module-layering FR-005/FR-006: none of the other 6 filter chains'
 * {@code securityMatcher} patterns cover {@code /actuator/**} (they're each scoped to their own
 * {@code /api/v1/**} prefix), so without an explicit chain here actuator endpoints would fall
 * through ungoverned by Spring Security entirely - the same "silently public/unprotected by
 * omission" bug class this project has already found and fixed twice before (014, 016) for
 * ordinary endpoints, avoided here proactively. {@code @Order(7)} is the next free slot after
 * booking's {@code @Order(6)}. Only {@code /actuator/health} is permitted; everything else under
 * {@code /actuator/**} is explicitly denied - defense in depth alongside
 * {@code management.endpoints.web.exposure.include=health} in application.yml, which already
 * means no other actuator endpoint is even registered.
 */
@Configuration
public class ActuatorSecurityConfig {

    @Bean
    @Order(7)
    public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/actuator/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health")
                        .permitAll()
                        .anyRequest()
                        .denyAll());
        return http.build();
    }
}
