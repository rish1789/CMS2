package com.cms.clinical;

import java.util.UUID;

/** No consultation note exists yet for this booking. */
public class ConsultationNoteNotFoundException extends RuntimeException {

    public ConsultationNoteNotFoundException(UUID bookingId) {
        super("No consultation note exists for booking " + bookingId);
    }
}
