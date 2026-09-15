package com.cms.booking.dto;

import java.util.UUID;

/** {@code applicable=false} for a Fixed-Time Session's Booking, or one whose own Slot is already resolved - {@code position} is always null alongside it, never a numeric value. */
public record QueuePositionResponse(UUID bookingId, boolean applicable, Integer position) {}
