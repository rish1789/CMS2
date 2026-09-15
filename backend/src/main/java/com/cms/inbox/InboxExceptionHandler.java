package com.cms.inbox;

import com.cms.identity.api.dto.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this feature's exception types to contracts/inbox.md's error shapes. */
@RestControllerAdvice
public class InboxExceptionHandler {

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ErrorResponse> handleForbidden(ForbiddenException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ErrorResponse.of("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(InboxItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(InboxItemNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("INBOX_ITEM_NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(AlreadyClaimedException.class)
    public ResponseEntity<ErrorResponse> handleAlreadyClaimed(AlreadyClaimedException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("ALREADY_CLAIMED", e.getMessage()));
    }

    @ExceptionHandler(NotClaimantException.class)
    public ResponseEntity<ErrorResponse> handleNotClaimant(NotClaimantException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse.of("NOT_CLAIMANT", e.getMessage()));
    }
}
