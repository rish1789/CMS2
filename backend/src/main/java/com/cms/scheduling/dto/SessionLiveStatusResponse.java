package com.cms.scheduling.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 061-doctor-live-status (data-model.md, contracts/doctor-live-status.md). {@code
 * applicable=false} only for a Queue-mode session, always paired with every other field {@code
 * null} - mirrors {@link SessionDelayResponse}'s existing contract exactly.
 */
public record SessionLiveStatusResponse(
        UUID sessionId,
        boolean applicable,
        String status,
        Integer currentPatientOrdinal,
        Integer expectedPatientOrdinal,
        Integer deviationMinutes,
        LocalTime firstSlotTime,
        LocalDate operationalDay) {}
