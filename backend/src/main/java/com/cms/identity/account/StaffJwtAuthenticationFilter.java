package com.cms.identity.account;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads a {@code Bearer} staff JWT (if present) and, when valid, populates the
 * {@link SecurityContextHolder} with the authenticated Account ID as principal - so
 * {@code .authenticated()} in {@link SecurityConfig} rejects a missing/invalid token with
 * 401, while the specific "is this account a ClinicAdmin for THIS clinic" check (403 vs
 * authenticated) happens at the controller/service layer, since that's per-clinic and not
 * expressible as a static security-config rule.
 *
 * <p><b>Deliberately NOT a {@code @Component}</b>: any {@code Filter}-typed bean gets
 * auto-registered by Spring Boot as a global servlet filter AND auto-detected by every
 * {@code @WebMvcTest} slice across the whole application (a well-known gotcha - filters
 * aren't excluded by the web-slice the way ordinary {@code @Component} beans are). That
 * broke 001's and 002's own, unrelated contract tests the first time this was tried as a
 * {@code @Component}. Instead, {@link SecurityConfig} constructs it directly with {@code
 * new} and wires it into its own chain only, via {@code addFilterBefore(...)}.
 */
public class StaffJwtAuthenticationFilter extends OncePerRequestFilter {

    private final StaffJwtService staffJwtService;

    public StaffJwtAuthenticationFilter(StaffJwtService staffJwtService) {
        this.staffJwtService = staffJwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Optional<UUID> accountId = staffJwtService.validateAndGetAccountId(token);
            accountId.ifPresent(id -> SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(
                            id, null, List.of(new SimpleGrantedAuthority("ROLE_STAFF")))));
        }
        filterChain.doFilter(request, response);
    }
}
