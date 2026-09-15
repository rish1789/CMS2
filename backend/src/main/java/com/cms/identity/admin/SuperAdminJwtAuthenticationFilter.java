package com.cms.identity.admin;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Reads a {@code Bearer} Super Admin JWT (if present) and, when valid, populates the
 * {@link SecurityContextHolder} with the authenticated username as principal - mirrors
 * {@code com.cms.identity.account.StaffJwtAuthenticationFilter} exactly, including why
 * this is deliberately NOT a {@code @Component} (a {@code Filter}-typed bean is
 * auto-registered globally and auto-detected by every {@code @WebMvcTest} slice, which
 * broke unrelated contract tests the first time that was tried for the staff equivalent
 * of this filter). {@link SuperAdminSecurityConfig} constructs it directly with {@code
 * new} and wires it into its own chain only.
 */
public class SuperAdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SuperAdminJwtService superAdminJwtService;

    public SuperAdminJwtAuthenticationFilter(SuperAdminJwtService superAdminJwtService) {
        this.superAdminJwtService = superAdminJwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring("Bearer ".length());
            Optional<String> username = superAdminJwtService.validateAndGetUsername(token);
            username.ifPresent(name -> SecurityContextHolder.getContext()
                    .setAuthentication(new UsernamePasswordAuthenticationToken(
                            name, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))));
        }
        filterChain.doFilter(request, response);
    }
}
