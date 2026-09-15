package com.cms.booking.dto;

import com.cms.scheduling.Session;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** patient-booking-flow-rebuild: one entry in the patient-facing Queue-session browse list - mirrors {@link OpenSlotResponse}'s shape at the Session level instead of the Slot level. */
public record QueueSessionResponse(
        UUID sessionId,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        List<AppointmentTypeResponse> appointmentTypes) {

    public static QueueSessionResponse of(Session session, String doctorName, List<AppointmentTypeResponse> appointmentTypes) {
        return new QueueSessionResponse(
                session.getId(),
                session.getDoctorProfile().getId(),
                doctorName,
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                appointmentTypes);
    }
}
