package com.cms.booking;

import java.util.UUID;

/** 017 FR-003: neither the Appointment Type's own override nor the doctor's default fee is set - a hard block, never a silent default. */
public class NoFeeConfiguredException extends RuntimeException {

    public NoFeeConfiguredException(UUID doctorProfileId, UUID appointmentTypeId) {
        super("No fee configured for doctor " + doctorProfileId + " / appointment type " + appointmentTypeId);
    }
}
