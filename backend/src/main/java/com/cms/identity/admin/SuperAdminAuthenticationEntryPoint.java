package com.cms.identity.admin;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Writes {@code {"error":"UNAUTHORIZED"}} instead of Spring Security's default Basic Auth
 * challenge - mirrors {@code com.cms.identity.account.StaffAuthenticationEntryPoint}.
 * Replaces {@link SuperAdminSecurityConfig}'s prior {@code httpBasic()} default entry
 * point now that Basic Auth is no longer accepted on this chain (040-super-admin-rbac-login).
 */
@Component
public class SuperAdminAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
    }
}
