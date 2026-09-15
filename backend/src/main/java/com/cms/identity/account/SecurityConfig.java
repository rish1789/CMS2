package com.cms.identity.account;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Scoped to {@code /api/v1/clinics/**} (not {@code anyRequest()}) as of
     * 002-patient-account-login, so this chain and the patient-facing one
     * ({@code com.cms.patient.account.SecurityConfig}) never both match the same
     * request - each identity system owns exactly its own path prefix, keeping "no
     * shared authentication logic" (FR-008 of 002) true structurally, not just by
     * convention.
     *
     * <p>Extended by 004-staff-onboarding-direct-hire (not a new chain, per the note this
     * comment originally left for that feature): {@code POST /api/v1/clinics/register}
     * stays public; {@code POST /api/v1/clinics/*}{@code /staff} now requires a valid
     * staff JWT ({@link StaffJwtAuthenticationFilter}, populated before Spring's own
     * username/password filter runs). The finer-grained "is this account a ClinicAdmin
     * for THIS specific clinic" check (403 vs merely-authenticated) happens in
     * {@code StaffOnboardingController}/{@code StaffOnboardingService}, not here - a
     * per-path-variable authorization rule isn't expressible as a static security-config
     * matcher.
     *
     * <p>Extended again by 005-last-active-clinicadmin-protection: {@code POST
     * /api/v1/clinics/*}{@code /staff/*}{@code /deactivate} also requires a valid staff
     * JWT, for the same reason - without an explicit matcher here it would silently fall
     * through to {@code anyRequest().permitAll()} below and become a public endpoint.
     *
     * <p>Extended again by 013-recurring-schedule-definition: {@code POST}/{@code GET
     * /api/v1/clinics/*}{@code /doctors/*}{@code /schedules} also require a valid staff
     * JWT - the finer-grained "ClinicAdmin at this clinic, or the doctor themselves"
     * check happens in {@code ScheduleService}, the same per-path-variable-authorization
     * split used by every other matcher added to this chain.
     *
     * <p>Extended again by 016-schedule-edit-non-retroactivity: {@code PATCH
     * /api/v1/clinics/*}{@code /doctors/*}{@code /schedules/*} also requires a valid staff
     * JWT - a distinct path (one more segment) from the create/list matchers above, so it
     * needs its own explicit matcher for the same reason every prior addition to this
     * chain did; without it this would silently fall through to {@code anyRequest().permitAll()}.
     *
     * <p>Extended again by 020-staff-assisted-fixed-time-booking: {@code POST
     * /api/v1/clinics/*}{@code /slots/*}{@code /book} also requires a valid staff JWT -
     * the finer-grained "active Operations or ClinicAdmin at this clinic" check happens
     * in {@code StaffBookingService}, the same split used by every other matcher here.
     *
     * <p>Extended again by 022-queue-token-booking: {@code POST /api/v1/clinics/*}{@code
     * /sessions/*}{@code /queue-bookings} also requires a valid staff JWT - the same
     * "active Operations or ClinicAdmin" split, this time in {@code StaffQueueBookingService}.
     *
     * <p>Extended again by 025-walk-in-priority-insertion: {@code POST /api/v1/clinics/*}
     * {@code /sessions/*}{@code /walk-in} also requires a valid staff JWT - the same
     * "active Operations or ClinicAdmin" split, this time in {@code WalkInInsertionService}.
     *
     * <p>Extended again by 026-session-delay-tracking: {@code POST /api/v1/clinics/*}
     * {@code /slots/*}{@code /complete} requires a valid staff JWT with the same "active
     * Operations or ClinicAdmin" split in {@code SlotCompletionService}; {@code GET
     * /api/v1/clinics/*}{@code /sessions/*}{@code /delay} requires only a valid staff JWT
     * (no role restriction - staff or the doctor may both view, per spec FR-006).
     *
     * <p>Extended again by 027-queue-position-tracking: {@code GET /api/v1/clinics/*}
     * {@code /bookings/*}{@code /queue-position} requires a valid staff JWT with any active
     * role at the clinic (no Operations/ClinicAdmin-only gate) in {@code
     * StaffQueuePositionController}.
     *
     * <p>Extended again by 028-individual-booking-cancellation: {@code POST
     * /api/v1/clinics/*}{@code /bookings/*}{@code /cancel} requires a valid staff JWT with
     * any active role at the clinic (same as 027's queue-position, no time restriction) in
     * {@code StaffBookingCancellationController}.
     *
     * <p>Extended again by 029-whole-day-session-cancellation: {@code POST
     * /api/v1/clinics/*}{@code /sessions/*}{@code /cancel} requires a valid staff JWT with
     * the standard Operations-or-ClinicAdmin write-action gate (not 028's any-active-role
     * one) in {@code SessionCancellationController}.
     *
     * <p>Extended again by 030-partial-cutoff-session-cancellation: {@code POST
     * /api/v1/clinics/*}{@code /sessions/*}{@code /cancel-from-cutoff} requires a valid
     * staff JWT with the same Operations-or-ClinicAdmin write-action gate in {@code
     * SessionPartialCancellationController}.
     *
     * <p>Extended again by 031-waitlist-matching-longest-waiting: {@code POST
     * /api/v1/clinics/*}{@code /waitlist} requires a valid staff JWT (any active role at
     * the clinic, mirrored to require Operations-or-ClinicAdmin specifically inside
     * {@code StaffWaitlistController} itself) - staff joining a patient onto the waitlist
     * on their behalf.
     *
     * <p>Extended again by 034-consultation-note-creation: {@code POST}/{@code GET
     * /api/v1/clinics/*}{@code /bookings/*}{@code /consultation-notes} require a valid
     * staff JWT - the finer-grained "the acting doctor is this specific booking's treating
     * doctor, no ClinicAdmin override" check happens in {@code ConsultationNoteService}.
     *
     * <p>Extended again by 035-prescription-and-items-creation: {@code POST}/{@code GET
     * /api/v1/clinics/*}{@code /bookings/*}{@code /prescriptions} require a valid staff
     * JWT - the same treating-doctor-only check happens in {@code PrescriptionService},
     * via the same shared {@code TreatingDoctorAuthorizationService} 034 now also uses.
     *
     * <p>Extended again by 036-external-record-reference: {@code POST}/{@code GET
     * /api/v1/clinics/*}{@code /bookings/*}{@code /external-record-references} require a
     * valid staff JWT - the same treating-doctor-only check, via the same shared
     * {@code TreatingDoctorAuthorizationService}, its third caller.
     *
     * <p>Extended again by 037-patient-immediate-anonymization: {@code POST
     * /api/v1/clinics/*}{@code /patients/*}{@code /anonymize} requires a valid staff JWT
     * with the standard Operations-or-ClinicAdmin write-action gate, checked in
     * {@code StaffPatientAnonymizationController}.
     *
     * <p>Extended again by 038-unified-realtime-inbox: {@code GET}/{@code GET .../stream}/
     * {@code POST .../claim}/{@code POST .../release}/{@code POST .../resolve} under
     * {@code /api/v1/clinics/*}{@code /inbox/**} all require a valid staff JWT - the same
     * Operations-or-ClinicAdmin write-action gate, checked in {@code InboxItemService}.
     *
     * <p>Extended again by 041-staff-console-pickers: {@code GET /api/v1/clinics/mine},
     * {@code GET .../sessions}, {@code GET .../sessions/*}{@code /day-sheet}, {@code GET
     * .../doctors}, {@code GET .../staff}, and {@code GET .../patients/search} all require a
     * valid staff JWT - each one's finer-grained "active role at this specific clinic" check
     * happens in its own controller, the same per-path-variable-authorization split used by
     * every other matcher added to this chain. {@code /clinics/mine} is not clinic-scoped at
     * all (it lists whichever clinics the caller belongs to), so it has no such check.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain filterChain(HttpSecurity http, StaffJwtService staffJwtService,
            StaffAuthenticationEntryPoint staffAuthenticationEntryPoint) throws Exception {
        http.securityMatcher("/api/v1/clinics/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new StaffJwtAuthenticationFilter(staffJwtService), UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint(staffAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth.requestMatchers(HttpMethod.POST, "/api/v1/clinics/register")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/staff")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/staff/*/deactivate")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/doctors/*/schedules")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/doctors/*/schedules")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/clinics/*/doctors/*/schedules/*")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/slots/*/book")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/queue-bookings")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/walk-in")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/slots/*/complete")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions/*/delay")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/bookings/*/queue-position")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/cancel")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/cancel")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/sessions/*/cancel-from-cutoff")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/waitlist")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/consultation-notes")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/bookings/*/consultation-notes")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/prescriptions")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/bookings/*/prescriptions")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/bookings/*/external-record-references")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/bookings/*/external-record-references")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/patients/*/anonymize")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/inbox")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/inbox/stream")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/inbox/*/claim")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/inbox/*/release")
                        .authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/clinics/*/inbox/*/resolve")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/mine")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/sessions/*/day-sheet")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/doctors")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/staff")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/clinics/*/patients/search")
                        .authenticated()
                        .anyRequest()
                        .permitAll());
        return http.build();
    }

    /**
     * 004-staff-onboarding-direct-hire: the staff login endpoint. Doesn't overlap
     * {@code /api/v1/clinics/**}, so this is a genuinely separate, non-competing chain
     * (unlike the onboarding endpoint above, which extends the existing one) - public,
     * since logging in is how a staff JWT is obtained in the first place.
     */
    @Bean
    @Order(4)
    public SecurityFilterChain staffAuthFilterChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/api/v1/staff/**")
                .csrf(csrf -> csrf.disable())
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /** Extracts the authenticated staff Account ID set by {@link StaffJwtAuthenticationFilter}. */
    public static java.util.UUID currentAccountId(
            org.springframework.security.core.Authentication authentication) {
        if (authentication instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof java.util.UUID accountId) {
            return accountId;
        }
        throw new IllegalStateException("No authenticated staff Account in the security context");
    }
}
