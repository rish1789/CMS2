package com.cms.booking.dto;

import java.time.LocalTime;

public record PartialCancellationRequest(LocalTime cutoffTime, LocalTime toTime) {}
