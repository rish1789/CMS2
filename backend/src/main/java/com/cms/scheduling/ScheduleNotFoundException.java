package com.cms.scheduling;

import java.util.UUID;

/** 016: no Schedule with this id, or it doesn't belong to the clinic/doctor named in the path (research.md). */
public class ScheduleNotFoundException extends RuntimeException {

    public ScheduleNotFoundException(UUID scheduleId) {
        super("No Schedule with id " + scheduleId);
    }
}
