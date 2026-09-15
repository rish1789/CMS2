package com.cms.booking.dto;

import jakarta.validation.constraints.Size;

/**
 * patient-cancellation-reason: body of {@code POST /api/v1/patients/bookings/{id}/cancel}.
 * {@code reason} is a plain String, validated against {@code BookingCancellationReason} in the
 * controller (not via Bean Validation) - a missing/invalid value needs its own
 * CANCELLATION_REASON_REQUIRED/INVALID_CANCELLATION_REASON error codes, not a generic one
 * (mirrors {@code RejectRequest}'s identical precedent for the admin verification queues).
 * {@code reasonDetail} has no such enum, so it gets a plain length cap instead.
 */
public record CancelBookingRequest(String reason, @Size(max = 500) String reasonDetail) {}
