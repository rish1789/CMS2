package com.cms.booking;

import java.util.UUID;

/** 017 FR-004: no AppointmentType with this id, or it belongs to a different doctor than named. */
public class AppointmentTypeNotFoundException extends RuntimeException {

    public AppointmentTypeNotFoundException(UUID appointmentTypeId) {
        super("No Appointment Type with id " + appointmentTypeId + " for this doctor");
    }
}
