package com.cms.scheduling;

import java.util.UUID;

/** 014: the submitted Schedule shares a day and an overlapping time range with an existing Schedule for this doctor. */
public class ScheduleOverlapException extends RuntimeException {

    public ScheduleOverlapException(UUID doctorProfileId, UUID conflictingScheduleId) {
        super("New Schedule overlaps existing Schedule " + conflictingScheduleId + " for Doctor Profile " + doctorProfileId);
    }
}
