package com.cms.booking.dto;

import java.util.UUID;

public record SessionCancellationResponse(UUID sessionId, int bookingsCancelled) {}
