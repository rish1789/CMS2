package com.cms.scheduling.dto;

import com.cms.scheduling.Schedule;
import com.cms.scheduling.ScheduleMode;
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
        Integer slotIntervalMinutes) {

    public static ScheduleResponse of(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getClinic().getId(),
                schedule.getDoctorProfile().getId(),
                schedule.getDaysOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getMode(),
                schedule.getSlotIntervalMinutes());
    }
}
