package com.cms.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.RequestDispatcher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Pure unit test (no Spring context) - {@link ApiErrorController} is a plain method, so this
 * exercises it directly rather than through a full {@code /error} servlet dispatch.
 */
class ApiErrorControllerTest {

    private final ApiErrorController controller = new ApiErrorController();

    @Test
    void unexpectedExceptionGetsAGenericSafeBodyNotItsOwnMessage() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                RequestDispatcher.ERROR_EXCEPTION,
                new IllegalStateException("super secret internal detail that must never reach a client"));
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);

        ResponseEntity<Map<String, String>> response = controller.handleError(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("error", "INTERNAL_SERVER_ERROR");
        assertThat(response.getBody().toString()).doesNotContain("super secret internal detail");
    }

    @Test
    void preservesA4xxStatusWithAGenericRequestErrorCode() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 404);

        ResponseEntity<Map<String, String>> response = controller.handleError(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("error", "REQUEST_ERROR");
    }

    @Test
    void defaultsTo500WhenNoStatusAttributeIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<Map<String, String>> response = controller.handleError(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
