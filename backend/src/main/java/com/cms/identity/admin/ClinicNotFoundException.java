package com.cms.identity.admin;

import java.util.UUID;

public class ClinicNotFoundException extends RuntimeException {

    public ClinicNotFoundException(UUID clinicId) {
        super("No clinic with id " + clinicId);
    }
}
