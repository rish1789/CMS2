package com.cms.booking.dto;

import java.util.List;
import java.util.UUID;

public record BatchCancelRequest(List<UUID> bookingIds) {}
