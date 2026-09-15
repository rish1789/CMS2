package com.cms.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Pure unit test (no Spring context) for request-id generation, echoing, and MDC lifecycle. */
class RequestIdFilterTest {

    @Test
    void generatesARequestIdWhenNoneProvidedAndEchoesItBack() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/whatever");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String[] observedDuringRequest = new String[1];
        FilterChain chain = (req, res) -> observedDuringRequest[0] = MDC.get(RequestIdFilter.MDC_KEY);

        filter.doFilter(request, response, chain);

        assertThat(observedDuringRequest[0]).isNotBlank();
        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo(observedDuringRequest[0]);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull(); // cleared once the request completes
    }

    @Test
    void reusesAnIncomingRequestIdInsteadOfGeneratingANewOne() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/whatever");
        request.addHeader(RequestIdFilter.REQUEST_ID_HEADER, "caller-supplied-id-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {};

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(RequestIdFilter.REQUEST_ID_HEADER)).isEqualTo("caller-supplied-id-123");
    }
}
