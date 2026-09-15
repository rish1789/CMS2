package com.cms.patient.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Patient-facing security filter chain - entirely separate from
 * {@code com.cms.identity.account.SecurityConfig} (001), per FR-008's "no shared
 * authentication logic" requirement. Scoped to {@code /api/v1/patients/**} only, so the
 * two chains never overlap; each is ordered so Spring Security dispatches a request to
 * exactly one of them based on path. Signup/login are the only endpoints this feature
 * adds, so both are permitted here; a later patient-authenticated endpoint would extend
 * this chain, not 001's.
 *
 * <p>Does <b>not</b> declare its own {@code PasswordEncoder} bean - the one already
 * registered by 001's {@code SecurityConfig} is a stateless, framework-level utility
 * (not identity/auth-flow logic), so reusing the single application-wide bean via type-
 * based injection is not a violation of the "no shared authentication logic" boundary.
 *
 * <p>Extended by 021-patient-self-service-booking: {@code GET /api/v1/patients/clinics/*}{@code
 * /slots} and {@code POST /api/v1/patients/clinics/*}{@code /slots/*}{@code /book} now
 * require a valid Patient Account JWT ({@link PatientJwtAuthenticationFilter}, populated
 * before Spring's own username/password filter runs) - the first authenticated endpoints
 * this chain has ever needed, mirroring 001/004's {@code StaffJwtAuthenticationFilter}
 * pattern exactly. Without an explicit matcher here, either path would silently fall
 * through to {@code anyRequest().permitAll()} below and become public - the same bug class
 * already caught and fixed twice this session (014, 020) on the staff chain.
 *
 * <p>Extended again by 022-queue-token-booking: {@code POST /api/v1/patients/clinics/*}{@code
 * /sessions/*}{@code /queue-bookings} also requires a valid Patient Account JWT, for the
 * same reason.
 *
 * <p>Extended again by 027-queue-position-tracking: {@code GET /api/v1/patients/bookings/*}
 * {@code /queue-position} also requires a valid Patient Account JWT - ownership of the
 * Booking itself (not clinic membership) is the actual access boundary, enforced in
 * {@code PatientQueuePositionController}.
 *
 * <p>Extended again by 028-individual-booking-cancellation: {@code POST
 * /api/v1/patients/bookings/*}{@code /cancel} also requires a valid Patient Account JWT -
 * ownership plus the 2-hour cutoff are enforced in {@code PatientBookingCancellationController}.
 *
 * <p>Extended again by 031-waitlist-matching-longest-waiting: {@code POST
 * /api/v1/patients/clinics/*}{@code /waitlist} also requires a valid Patient Account JWT -
 * a patient joining the waitlist for themselves, in {@code PatientWaitlistController}.
 *
 * <p>Extended again by 032-self-service-waitlist-claim: {@code POST
 * /api/v1/patients/waitlist-entries/*}{@code /claim} and {@code .../decline} also require a
 * valid Patient Account JWT - ownership of the Waitlist Entry itself (not clinic membership)
 * is the access boundary, enforced in {@code PatientWaitlistClaimController}.
 *
 * <p>Extended again by patient-booking-flow-rebuild: {@code GET /api/v1/patients/clinics},
 * {@code GET /api/v1/patients/bookings}, and {@code GET /api/v1/patients/clinics/*}{@code
 * /queue-sessions} also require a valid Patient Account JWT - the three new browse/pick
 * endpoints ("My clinics", "My bookings", Queue-session picker) replacing raw-ID entry forms.
 *
 * <p>Extended again: {@code GET /api/v1/patients/clinics/*}{@code /doctors} also requires a
 * valid Patient Account JWT - {@code PatientBookingController.listDoctors} backs the doctor
 * picker replacing {@code JoinWaitlistForm}'s raw {@code doctorProfileId} text field.
 *
 * <p>Extended again: {@code GET /api/v1/patients/doctors/*}{@code /appointment-types} also
 * requires a valid Patient Account JWT - {@code PatientBookingController.listAppointmentTypes}
 * backs the picker replacing {@code ClaimOfferCard}'s raw "Appointment Type ID" text field.
 */
// Explicit bean name: Spring's default (the unqualified simple class name) collides with
// com.cms.identity.account.SecurityConfig's own default bean name, which a full application
// context boot rejects as a ConflictingBeanDefinitionException - never caught by this
// session's own tests, since every @SpringBootTest here is Testcontainers-gated and fails
// before Spring ever attempts to start the context. Found only by actually booting the full
// app (038's own quickstart run). Bean is never referenced by name anywhere, so this is safe.
@Configuration("patientAccountSecurityConfig")
public class SecurityConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain patientFilterChain(
            HttpSecurity http, JwtService jwtService, PatientAuthenticationEntryPoint patientAuthenticationEntryPoint)
            throws Exception {
        http.securityMatcher("/api/v1/patients/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new PatientJwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(patientAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.GET, "/api/v1/patients/clinics/*/slots")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/clinics/*/slots/*/book")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/clinics/*/sessions/*/queue-bookings")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings/*/queue-position")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/bookings/*/cancel")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/clinics/*/waitlist")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/waitlist-entries/*/claim")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/patients/waitlist-entries/*/decline")
                        .authenticated()
                        // _diagnostics [HIGH] - [WAITLIST_CLAIM] - [WORKFLOW_GAP]: added proactively
                        // alongside the new endpoint, per this codebase's own established discipline
                        // of never letting a new authenticated path fall through to anyRequest().permitAll().
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/waitlist-entries")
                        .authenticated()
                        // patient-booking-flow-rebuild: the three new browse/pick endpoints -
                        // added proactively alongside each new controller, per this codebase's
                        // own established discipline of never letting a new authenticated path
                        // fall through to anyRequest().permitAll().
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/clinics")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/bookings")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/clinics/*/queue-sessions")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/clinics/*/doctors")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/patients/doctors/*/appointment-types")
                        .authenticated()
                        .anyRequest()
                        .permitAll());
        return http.build();
    }

    /** Extracts the authenticated Patient Account ID set by {@link PatientJwtAuthenticationFilter}. */
    public static java.util.UUID currentPatientAccountId(
            org.springframework.security.core.Authentication authentication) {
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof java.util.UUID patientAccountId) {
            return patientAccountId;
        }
        throw new IllegalStateException("No authenticated Patient Account in the security context");
    }
}
