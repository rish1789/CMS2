package com.cms.patient.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 021-patient-self-service-booking: mirrors {@code com.cms.identity.account.StaffJwtAuthenticationFilter}
 * exactly in shape - reads a {@code Bearer} token, and when it is a valid, non-expired
 * Patient Account JWT (per {@link JwtService#isPatientToken}, which checks {@code aud=patient}),
 * populates {@link SecurityContextHolder} with the {@code PatientAccount} id as principal so
 * {@code .authenticated()} in {@link SecurityConfig} rejects a missing/invalid token with 401.
 *
 * <p>Deliberately NOT a {@code @Component}, for the exact reason {@code
 * StaffJwtAuthenticationFilter}'s own javadoc documents: a {@code Filter}-typed bean
 * auto-registers as a global servlet filter and gets swept into every unrelated
 * {@code @WebMvcTest} slice. {@link SecurityConfig} constructs it directly with {@code new}
 * and wires it into its own chain only.
 */
public class PatientJwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public PatientJwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            if (jwtService.isPatientToken(token)) {
                UUID patientAccountId = UUID.fromString(jwtService.parse(token).getSubject());
                SecurityContextHolder.getContext()
                        .setAuthentication(new UsernamePasswordAuthenticationToken(
                                patientAccountId, null, List.of(new SimpleGrantedAuthority("ROLE_PATIENT"))));
            }
        }
        filterChain.doFilter(request, response);
    }
}
