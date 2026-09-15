package com.cms.booking.dto;

import com.cms.booking.AppointmentType;
import java.math.BigDecimal;
import java.util.UUID;

public record AppointmentTypeResponse(UUID id, UUID doctorProfileId, String name, BigDecimal feeOverride) {

    public static AppointmentTypeResponse of(AppointmentType appointmentType) {
        return new AppointmentTypeResponse(
                appointmentType.getId(),
                appointmentType.getDoctorProfile().getId(),
                appointmentType.getName(),
                appointmentType.getFeeOverride());
    }
}
