package com.cms.patient.account;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 021-patient-self-service-booking: mirrors {@code com.cms.identity.account.StaffAuthenticationEntryPoint}
 * - writes {@code {"error":"UNAUTHORIZED"}} instead of Spring Security's default empty 401
 * body. The first authenticated endpoint on the patient chain (021) is what first needed
 * this; signup/login (039) never did, since both are intentionally public.
 */
@Component
public class PatientAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\"}");
    }
}
