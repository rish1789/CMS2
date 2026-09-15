package com.cms.clinical;

import java.util.UUID;

/** FR-003: a consultation note already exists for this booking - exactly one per booking, ever. */
public class ConsultationNoteAlreadyExistsException extends RuntimeException {

    public ConsultationNoteAlreadyExistsException(UUID bookingId) {
        super("A consultation note already exists for booking " + bookingId);
    }
}
