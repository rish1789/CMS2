package com.cms.booking.dto;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #3): `GET .../protection/limit-override`. */
public record ClinicBookingLimitOverrideResponse(Integer maxActiveAppointments, int globalMax) {}
