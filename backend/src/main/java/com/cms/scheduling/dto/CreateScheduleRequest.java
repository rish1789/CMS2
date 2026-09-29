package com.cms.scheduling.dto;

import com.cms.scheduling.domain.ScheduleMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

public record CreateScheduleRequest(
        Set<DayOfWeek> daysOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleMode mode,
        Integer slotIntervalMinutes,
        // 055-schedule-break-window: both null (no break), or both set.
        LocalTime breakStartTime,
        LocalTime breakEndTime) {}
