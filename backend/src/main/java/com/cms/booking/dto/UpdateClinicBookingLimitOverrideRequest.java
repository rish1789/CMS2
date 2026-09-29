package com.cms.booking.dto;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #3): `PUT .../protection/limit-override`. */
public record UpdateClinicBookingLimitOverrideRequest(int maxActiveAppointments) {}
