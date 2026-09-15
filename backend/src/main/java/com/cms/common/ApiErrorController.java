package com.cms.common;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Last-resort safety net for any exception no module-specific {@code @RestControllerAdvice}
 * already maps (see e.g. {@code BookingExceptionHandler}, {@code ScheduleExceptionHandler},
 * {@code StaffExceptionHandler}...). Without this, a genuinely unanticipated exception (a bug,
 * not a modeled business-rule violation) fell through to Spring Boot's default {@code
 * BasicErrorController} body - not guaranteed to match this API's established {@code {error,
 * message}} shape, and not a contract this codebase wants to leave implicit.
 *
 * <p>Deliberately implemented by overriding Spring Boot's {@code /error} dispatch (this bean's
 * mere presence as an {@link ErrorController} disables the auto-configured {@code
 * BasicErrorController} - see {@code ErrorMvcAutoConfiguration}'s {@code
 * @ConditionalOnMissingBean(ErrorController.class)}) rather than adding another {@code
 * @ExceptionHandler(Exception.class)} in a competing {@code @RestControllerAdvice}: every
 * existing handler in this app declares no explicit {@code @Order}, which defaults to {@code
 * Ordered.LOWEST_PRECEDENCE} - the exact same value an explicitly-lowest-ordered competing
 * advice would use - so a tie between two beans at that value is broken by an unspecified
 * bean-discovery order, not a guaranteed "existing handlers always win." (Confirmed by an
 * earlier version of this class, built as a competing advice, actually intercepting {@code
 * MissingRequiredFieldException}/{@code EmailAlreadyInUseException} ahead of {@code
 * GlobalExceptionHandler} in a real test run - not a hypothetical risk.) The {@code /error}
 * dispatch path is structurally reached only after every {@code @RestControllerAdvice}'s
 * {@code @ExceptionHandler} resolution has already run and found no match, so no such race is
 * possible here.
 *
 * <p>Logs the full exception server-side (stack trace included, at ERROR, correlated to the
 * request via {@link RequestIdFilter}'s MDC entry) but returns only a generic, client-safe
 * message - the exception's own message and stack trace (which can carry internal detail: SQL
 * fragments, field names, library internals) never reach the response body.
 */
@RestController
public class ApiErrorController implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorController.class);

    @RequestMapping("/error")
    public ResponseEntity<Map<String, String>> handleError(HttpServletRequest request) {
        Throwable cause = (Throwable) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        HttpStatus status = statusAttribute != null
                ? HttpStatus.valueOf((Integer) statusAttribute)
                : HttpStatus.INTERNAL_SERVER_ERROR;

        if (cause != null) {
            log.error("Unhandled exception reached the global fallback error controller", cause);
        } else {
            log.warn(
                    "Request reached the global fallback error controller with no exception attribute (status {})",
                    status.value());
        }

        String code = status.is4xxClientError() ? "REQUEST_ERROR" : "INTERNAL_SERVER_ERROR";
        String message = status.is4xxClientError()
                ? "The request could not be processed."
                : "An unexpected error occurred. Please try again.";
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }
}
