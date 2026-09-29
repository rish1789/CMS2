package com.cms.scheduling.dto;

import com.cms.scheduling.domain.Schedule;
import com.cms.scheduling.domain.ScheduleMode;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;
import java.util.UUID;

public record ScheduleResponse(
        UUID id,
        UUID clinicId,
        UUID doctorProfileId,
        Set<DayOfWeek> daysOfWeek,
        LocalTime startTime,
        LocalTime endTime,
        ScheduleMode mode,
        Integer slotIntervalMinutes,
        LocalTime breakStartTime,
        LocalTime breakEndTime) {

    public static ScheduleResponse of(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getClinic().getId(),
                schedule.getDoctorProfile().getId(),
                schedule.getDaysOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getMode(),
                schedule.getSlotIntervalMinutes(),
                schedule.getBreakStartTime(),
                schedule.getBreakEndTime());
    }
}
