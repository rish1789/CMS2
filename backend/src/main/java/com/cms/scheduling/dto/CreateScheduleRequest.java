package com.cms.scheduling.dto;

import com.cms.scheduling.ScheduleMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record CreateScheduleRequest(
        Set<DayOfWeek> daysOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleMode mode,
        Integer slotIntervalMinutes) {}
