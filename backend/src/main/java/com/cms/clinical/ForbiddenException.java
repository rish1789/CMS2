package com.cms.clinical;

/**
 * _diagnostics [SYSTEMIC] - [AUTHORIZATION] - [WRONG_DOMAIN_ERROR_MESSAGE]: this module used to
 * reuse {@code com.cms.scheduling.ForbiddenException} directly, whose hardcoded message
 * ("Not authorized to manage schedules for this doctor at this clinic") has nothing to do with
 * clinical documentation - it leaked verbatim to the frontend via the shared error-display
 * template's message precedence. Own exception, own message.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException() {
        super("Only the treating doctor may write or view clinical records for this booking");
    }
}
