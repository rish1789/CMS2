package com.cms.discovery;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 035: the public discovery search filter chain - entirely separate from every other
 * module's chain, scoped to {@code /api/v1/discovery/**} only, so it never competes with
 * {@code identity.account} ({@code @Order(1)}), {@code patient.account}
 * ({@code @Order(2)}), {@code identity.admin} ({@code @Order(3)}), or
 * {@code identity.account}'s staff chain ({@code @Order(4)}) - {@code @Order(5)} is the
 * next free slot. Permits every request unconditionally (FR-001): this endpoint never
 * reads or requires a credential of any kind.
 */
@Configuration
public class DiscoverySecurityConfig {

    @Bean
    @Order(5)
    public SecurityFilterChain discoveryFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/discovery/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
