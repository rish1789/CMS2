package com.cms.patient.record.dto;

import com.cms.booking.domain.Booking;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * 052-patient-clinical-hub T004 (data-model.md): one row in the Patient Hub's Bookings/
 * Consultations/Prescriptions/External Records sections. {@code slotStatus} is deliberately
 * included alongside {@code bookingStatus} - a cancelled Booking's Slot reverts to OPEN (028),
 * which the frontend uses to exclude cancelled bookings from the 3 clinical-record sections
 * (a cancelled appointment never had a consultation) while still showing them in Bookings.
 */
public record PatientBookingSummaryResponse(
        UUID bookingId,
        LocalDate sessionDate,
        LocalTime startTime,
        String doctorName,
        String appointmentTypeName,
        String bookingStatus,
        String slotStatus) {

    public static PatientBookingSummaryResponse from(Booking booking) {
        var slot = booking.getSlot();
        var session = slot.getSession();
        return new PatientBookingSummaryResponse(
                booking.getId(),
                session.getSessionDate(),
                slot.getStartTime(),
                session.getDoctorProfile().getAccount().getName(),
                booking.getAppointmentType().getName(),
                booking.getStatus().name(),
                slot.getStatus().name());
    }
}
