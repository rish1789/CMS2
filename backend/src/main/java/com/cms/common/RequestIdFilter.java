package com.cms.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id - reused from an incoming X-Request-Id header if the
 * caller (or a fronting proxy/load balancer) already set one, otherwise a fresh random one -
 * and makes it available to every log statement for the lifetime of the request via SLF4J's
 * MDC (see {@code logging.pattern.level} in application.yml, which renders {@code
 * %X{requestId}} into every log line). Echoed back on the response so a client or support
 * ticket can quote it when reporting an issue, and read by {@link GlobalFallbackExceptionHandler}
 * so an unhandled-exception log line can be correlated back to the request that caused it.
 *
 * <p>Registered as a plain servlet {@code FilterRegistrationBean} (see {@link
 * RequestLoggingConfig}), not a Spring Security filter - correlation ids are a transport-level
 * concern for every request, not an authentication concern for any one identity system.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
