package com.cms.scheduling.exception;



import java.util.UUID;

public class ClinicNotFoundException extends RuntimeException {

    public ClinicNotFoundException(UUID clinicId) {
        super("No Clinic with id " + clinicId);
    }
}
