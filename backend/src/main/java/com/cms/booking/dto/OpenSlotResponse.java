package com.cms.booking.dto;

import com.cms.scheduling.Slot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 021-patient-self-service-booking: one entry in the patient-facing open-Slots listing (research.md - omits resolved fee amounts). */
public record OpenSlotResponse(
        UUID slotId,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        LocalTime startTime,
        LocalTime endTime,
        List<AppointmentTypeResponse> appointmentTypes) {

    public static OpenSlotResponse of(Slot slot, String doctorName, List<AppointmentTypeResponse> appointmentTypes) {
        return new OpenSlotResponse(
                slot.getId(),
                slot.getSession().getDoctorProfile().getId(),
                doctorName,
                slot.getSession().getSessionDate(),
                slot.getStartTime(),
                slot.getEndTime(),
                appointmentTypes);
    }
}
