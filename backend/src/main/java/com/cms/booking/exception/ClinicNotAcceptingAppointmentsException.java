package com.cms.booking.exception;

import java.util.UUID;

/**
 * 062-rejected-clinic-gating (FR-001/FR-002): the clinic has been rejected by the Super Admin and
 * cannot take appointments through any booking path until it is restored.
 */
public class ClinicNotAcceptingAppointmentsException extends RuntimeException {

    public ClinicNotAcceptingAppointmentsException(UUID clinicId) {
        super("This clinic is not accepting appointments.");
    }
}
