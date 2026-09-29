package com.cms.booking.exception;

/** real-bug-fix 2026-09-17: names what's attached, mirroring com.cms.identity.admin.exception.DeletionBlockedException's own message-carrying shape for the same "block, don't cascade" pattern. */
public class SessionDeletionBlockedException extends RuntimeException {

    public SessionDeletionBlockedException(String message) {
        super(message);
    }
}
