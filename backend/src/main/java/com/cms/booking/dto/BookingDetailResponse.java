package com.cms.booking.dto;

import com.cms.booking.Booking;
import java.time.LocalDate;
import java.util.UUID;

/**
 * staff-console-audit-2026-09-10 P1: backs the entity-context header on the booking-scoped
 * staff tool pages (mark complete, cancel, consultation note, prescription, external record) -
 * previously none of them showed who/what the booking was for, since bareId routes had no way
 * to resolve a display name without this endpoint.
 */
public record BookingDetailResponse(
        UUID bookingId,
        UUID sessionId,
        UUID patientId,
        String patientName,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        String mode,
        String appointmentTypeName) {

    public static BookingDetailResponse from(Booking booking) {
        var session = booking.getSlot().getSession();
        return new BookingDetailResponse(
                booking.getId(),
                session.getId(),
                booking.getPatient().getId(),
                booking.getPatient().getName(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getSessionDate(),
                session.getMode().name(),
                booking.getAppointmentType().getName());
    }
}
