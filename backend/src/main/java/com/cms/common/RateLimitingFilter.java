package com.cms.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Fixed-window, per-client-IP request throttle for one specific sensitive endpoint (a login or a
 * public signup/registration path) - closes the "no rate limiting/lockout on login endpoints"
 * gap flagged by this session's audit (unbounded brute-force attempts against bcrypt hashes).
 *
 * <p>In-memory and per-instance: correct for a single backend instance (the current deployment
 * shape - see PRODUCTION_ROADMAP.md), not for a horizontally-scaled one, where a shared store
 * (e.g. Redis) would be needed instead. Deliberately dependency-free rather than adding
 * Bucket4j/Resilience4j for this scale - a fixed window per (client IP, protected path) is
 * enough to make brute force economically pointless without a new library.
 *
 * <p>One instance is constructed per protected path (see {@link RateLimitingConfig}) and wired
 * in as a plain servlet {@code Filter} via a {@code FilterRegistrationBean} scoped to that
 * path's URL pattern - not a {@code @Component}, so it is never auto-registered as a
 * container-wide filter and never auto-loaded into an unrelated {@code @WebMvcTest} slice, and
 * not wired into any Spring Security {@code SecurityFilterChain} - it doesn't touch that
 * already-delicate configuration (see {@code identity.account.SecurityConfig}'s own
 * bean-naming-collision history) at all.
 */
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final String protectedMethod;
    private final String protectedPath;
    private final int maxAttempts;
    private final long windowMillis;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

    public RateLimitingFilter(String protectedMethod, String protectedPath, int maxAttempts, long windowSeconds) {
        this(protectedMethod, protectedPath, maxAttempts, windowSeconds, Clock.systemUTC());
    }

    RateLimitingFilter(
            String protectedMethod, String protectedPath, int maxAttempts, long windowSeconds, Clock clock) {
        this.protectedMethod = protectedMethod;
        this.protectedPath = protectedPath;
        this.maxAttempts = maxAttempts;
        this.windowMillis = windowSeconds * 1000L;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!protectedMethod.equalsIgnoreCase(request.getMethod()) || !protectedPath.equals(request.getRequestURI())) {
            chain.doFilter(request, response);
            return;
        }

        String clientKey = request.getRemoteAddr();
        long now = clock.millis();
        Window window = windowsByClient.compute(clientKey, (key, existing) -> {
            if (existing == null || now - existing.startedAtMillis >= windowMillis) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });

        if (window.count.get() > maxAttempts) {
            log.warn("Rate limit exceeded for {} {} from {}", protectedMethod, protectedPath, clientKey);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(secondsRemaining(window, now)));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of("error", "RATE_LIMIT_EXCEEDED", "message", "Too many requests. Please try again later."));
            return;
        }

        chain.doFilter(request, response);
    }

    private long secondsRemaining(Window window, long now) {
        long elapsed = now - window.startedAtMillis;
        return Math.max(1, (windowMillis - elapsed) / 1000);
    }

    private static final class Window {
        private final long startedAtMillis;
        private final AtomicInteger count;

        private Window(long startedAtMillis, AtomicInteger count) {
            this.startedAtMillis = startedAtMillis;
            this.count = count;
        }
    }
}
