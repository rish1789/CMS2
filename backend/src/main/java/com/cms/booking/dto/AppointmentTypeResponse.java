package com.cms.booking.dto;

import com.cms.booking.domain.AppointmentType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 068-per-clinic-fees FR-011: {@code fee} is the type's effective price at the clinic the response
 * is for, or null when it is not bookable there - or when the response has no clinic context.
 */
public record AppointmentTypeResponse(UUID id, UUID doctorProfileId, String name, BigDecimal fee) {

    /** No clinic context - no price can be stated. */
    public static AppointmentTypeResponse of(AppointmentType appointmentType) {
        return of(appointmentType, null);
    }

    public static AppointmentTypeResponse of(AppointmentType appointmentType, BigDecimal fee) {
        return new AppointmentTypeResponse(
                appointmentType.getId(), appointmentType.getDoctorProfile().getId(), appointmentType.getName(), fee);
    }
}
