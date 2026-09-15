package com.cms.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Pure unit test (no Spring context) for the fixed-window throttle logic. */
class RateLimitingFilterTest {

    @Test
    void allowsUpToTheConfiguredLimitThenRejects() throws Exception {
        MutableClock clock = new MutableClock(0);
        RateLimitingFilter filter = new RateLimitingFilter("POST", "/api/v1/staff/login", 3, 60, clock);
        AtomicInteger passedThrough = new AtomicInteger();
        FilterChain chain = (req, res) -> passedThrough.incrementAndGet();

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(requestFrom("203.0.113.5"), response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(passedThrough.get()).isEqualTo(3);

        MockHttpServletResponse fourthResponse = new MockHttpServletResponse();
        filter.doFilter(requestFrom("203.0.113.5"), fourthResponse, chain);

        assertThat(fourthResponse.getStatus()).isEqualTo(429);
        assertThat(passedThrough.get()).isEqualTo(3); // the chain was never invoked for the rejected request
        assertThat(fourthResponse.getContentAsString()).contains("RATE_LIMIT_EXCEEDED");
        assertThat(fourthResponse.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void differentClientsAreTrackedIndependently() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter("POST", "/api/v1/staff/login", 1, 60, new MutableClock(0));
        FilterChain chain = (req, res) -> {};

        filter.doFilter(requestFrom("203.0.113.5"), new MockHttpServletResponse(), chain);

        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(requestFrom("198.51.100.9"), secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void windowResetsAfterConfiguredDuration() throws Exception {
        MutableClock clock = new MutableClock(0);
        RateLimitingFilter filter = new RateLimitingFilter("POST", "/api/v1/staff/login", 1, 60, clock);
        FilterChain chain = (req, res) -> {};

        filter.doFilter(requestFrom("203.0.113.5"), new MockHttpServletResponse(), chain);
        clock.advanceSeconds(61);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(requestFrom("203.0.113.5"), secondResponse, chain);

        assertThat(secondResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void unrelatedMethodsAndPathsPassThroughUnaffected() throws Exception {
        RateLimitingFilter filter = new RateLimitingFilter("POST", "/api/v1/staff/login", 1, 60, new MutableClock(0));
        AtomicInteger passedThrough = new AtomicInteger();
        FilterChain chain = (req, res) -> passedThrough.incrementAndGet();

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/staff/login"), new MockHttpServletResponse(), chain);
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/patients/login"), new MockHttpServletResponse(), chain);

        assertThat(passedThrough.get()).isEqualTo(2);
    }

    private static MockHttpServletRequest requestFrom(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/staff/login");
        request.setRemoteAddr(ip);
        return request;
    }

    private static final class MutableClock extends Clock {
        private final AtomicLong millis;

        MutableClock(long startMillis) {
            this.millis = new AtomicLong(startMillis);
        }

        void advanceSeconds(long seconds) {
            millis.addAndGet(seconds * 1000);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }
    }
}
