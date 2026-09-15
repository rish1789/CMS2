package com.cms.identity.admin;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Super Admin-facing security filter chain - entirely separate from 001's staff chain
 * and 002's patient chain, per 002's spec (FR-004): "no database-backed Super Admin
 * identity." Scoped to {@code /api/v1/admin/**} only, {@code @Order(3)} so it never
 * competes with the other two path-scoped chains.
 *
 * <p>040-super-admin-rbac-login: verifies a {@code SUPER_ADMIN}-audience bearer JWT
 * (issued at Clinic Portal login, {@code StaffAuthController}) via {@link
 * SuperAdminJwtAuthenticationFilter}, mirroring the staff/patient chains' shape exactly.
 * Basic Auth is no longer accepted here at all - the credential check itself still lives
 * in {@link SuperAdminAuthenticationService} (used only at login), and the {@code
 * superAdminUserDetailsService} bean below is now consumed only by that service, not by
 * this chain directly.
 */
@Configuration
public class SuperAdminSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminSecurityConfig.class);
    private static final String DEFAULT_USERNAME = "super-admin";

    @Bean
    @Order(3)
    public SecurityFilterChain adminFilterChain(
            HttpSecurity http,
            SuperAdminJwtService superAdminJwtService,
            SuperAdminAuthenticationEntryPoint superAdminAuthenticationEntryPoint)
            throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        new SuperAdminJwtAuthenticationFilter(superAdminJwtService),
                        UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(superAdminAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    /** Extracts the authenticated Super Admin username set by {@link SuperAdminJwtAuthenticationFilter}. */
    public static String currentSuperAdminUsername(org.springframework.security.core.Authentication authentication) {
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof String username) {
            return username;
        }
        throw new IllegalStateException("No authenticated Super Admin in the security context");
    }

    /**
     * If either value is left unconfigured, generates a random, unguessable password
     * for this run only (never a fixed/known default) - Super Admin actions are
     * effectively disabled until {@code SUPER_ADMIN_USERNAME}/{@code SUPER_ADMIN_PASSWORD}
     * are actually set, rather than silently accepting a blank credential (FR-009).
     */
    @Bean
    public UserDetailsService superAdminUserDetailsService(
            @Value("${admin.super-admin.username:}") String configuredUsername,
            @Value("${admin.super-admin.password:}") String configuredPassword,
            PasswordEncoder passwordEncoder) {
        String username = configuredUsername.isBlank() ? DEFAULT_USERNAME : configuredUsername;
        String password = configuredPassword.isBlank() ? UUID.randomUUID().toString() : configuredPassword;
        if (configuredPassword.isBlank()) {
            log.warn(
                    "admin.super-admin.password is not configured - generated a random password for this run only;"
                            + " Super Admin endpoints are unreachable until SUPER_ADMIN_USERNAME/SUPER_ADMIN_PASSWORD are set");
        }

        UserDetails superAdmin = User.withUsername(username)
                .password(passwordEncoder.encode(password))
                .roles("SUPER_ADMIN")
                .build();
        return new InMemoryUserDetailsManager(superAdmin);
    }
}
