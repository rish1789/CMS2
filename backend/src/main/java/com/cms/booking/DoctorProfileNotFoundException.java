package com.cms.booking;

import java.util.UUID;

public class DoctorProfileNotFoundException extends RuntimeException {

    public DoctorProfileNotFoundException(UUID doctorProfileId) {
        super("No Doctor Profile with id " + doctorProfileId);
    }
}
