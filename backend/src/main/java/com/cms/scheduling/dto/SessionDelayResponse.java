package com.cms.scheduling.dto;

import java.util.UUID;

/** {@code applicable=false} only for a Queue-mode Session, always paired with {@code delayMinutes=null} - never a numeric value (FR-007). */
public record SessionDelayResponse(UUID sessionId, boolean applicable, Integer delayMinutes) {}
