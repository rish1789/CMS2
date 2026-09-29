package com.cms.booking.exception;



import java.util.UUID;

public class DoctorProfileNotFoundException extends RuntimeException {

    public DoctorProfileNotFoundException(UUID doctorProfileId) {
        super("No Doctor Profile with id " + doctorProfileId);
    }
}
