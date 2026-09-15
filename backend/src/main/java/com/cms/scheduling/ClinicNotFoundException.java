package com.cms.scheduling;

import java.util.UUID;

public class ClinicNotFoundException extends RuntimeException {

    public ClinicNotFoundException(UUID clinicId) {
        super("No Clinic with id " + clinicId);
    }
}
