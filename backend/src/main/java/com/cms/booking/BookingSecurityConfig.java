package com.cms.booking;

import com.cms.identity.account.StaffAuthenticationEntryPoint;
import com.cms.identity.account.StaffJwtAuthenticationFilter;
import com.cms.identity.account.StaffJwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * 017: a genuinely separate chain from {@code com.cms.identity.account.SecurityConfig}'s
 * {@code /api/v1/clinics/**} - {@code AppointmentType}/{@code DoctorDefaultFee} are
 * doctor-scoped, not clinic-scoped (research.md), so their natural path doesn't overlap
 * that chain's prefix at all. Reuses the same staff-JWT machinery (001/004), not a new
 * auth mechanism. {@code @Order(6)} is the next free slot after discovery's {@code @Order(5)}.
 */
@Configuration
public class BookingSecurityConfig {

    @Bean
    @Order(6)
    public SecurityFilterChain bookingFilterChain(
            HttpSecurity http, StaffJwtService staffJwtService, StaffAuthenticationEntryPoint staffAuthenticationEntryPoint)
            throws Exception {
        http.securityMatcher("/api/v1/doctors/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new StaffJwtAuthenticationFilter(staffJwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(staffAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }
}
