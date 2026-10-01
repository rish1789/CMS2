package com.cms.booking.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * 068-per-clinic-fees (contracts/clinic-fees-api.md): one doctor's prices at one clinic.
 * {@code defaultFee}, {@code price} and {@code effectiveFee} are null when not set - a null
 * {@code effectiveFee} means the type is not bookable at this clinic.
 */
public record ClinicDoctorFeesResponse(
        UUID clinicId, UUID doctorProfileId, BigDecimal defaultFee, List<AppointmentTypeFee> appointmentTypes) {

    public record AppointmentTypeFee(UUID appointmentTypeId, String name, BigDecimal price, BigDecimal effectiveFee) {}
}
