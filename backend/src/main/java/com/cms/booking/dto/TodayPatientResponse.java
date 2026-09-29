package com.cms.booking.dto;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import java.time.LocalTime;
import java.util.UUID;

/**
 * real-bug-fix 2026-09-17: one row of the Find a Patient page's "today's patients" table -
 * every appointment-based and walk-in patient with an active Booking today, across every doctor
 * at the clinic. Deliberately its own DTO rather than reusing {@code SessionDaySheetResponse
 * .SlotDetail} - that one is scoped to a single, already-known Session (no doctor name/id of its
 * own needed); this one spans every doctor's Sessions for the day, so doctor identity is a
 * first-class field here.
 */
public record TodayPatientResponse(
        UUID bookingId,
        UUID patientId,
        String patientName,
        String patientPhone,
        UUID doctorProfileId,
        String doctorName,
        String mode,
        LocalTime startTime,
        Integer tokenNumber,
        String slotStatus,
        boolean isWalkIn) {

    public static TodayPatientResponse from(Booking booking) {
        Slot slot = booking.getSlot();
        Session session = slot.getSession();
        return new TodayPatientResponse(
                booking.getId(),
                booking.getPatient().getId(),
                booking.getPatient().getName(),
                booking.getPatient().getPhone(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getMode().name(),
                slot.getStartTime(),
                slot.getTokenNumber(),
                slot.getStatus().name(),
                booking.getSource() == BookingSource.WALK_IN);
    }
}
