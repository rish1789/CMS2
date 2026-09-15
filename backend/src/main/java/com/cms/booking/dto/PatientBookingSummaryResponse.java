package com.cms.booking.dto;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.booking.PaymentStatus;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * patient-booking-flow-rebuild: one row in "My bookings" - resolved clinic/doctor/appointment-type
 * names so the list is readable without a raw id in sight. {@code startTime}/{@code tokenNumber}
 * are the Fixed-Time/Queue-mode pair - exactly one is non-null per {@link Slot}'s own contract.
 */
public record PatientBookingSummaryResponse(
        UUID id,
        UUID clinicId,
        String clinicName,
        UUID doctorProfileId,
        String doctorName,
        String appointmentTypeName,
        ScheduleMode mode,
        LocalDate sessionDate,
        LocalTime startTime,
        Integer tokenNumber,
        BookingStatus status,
        PaymentStatus paymentStatus,
        BigDecimal lockedFee,
        Instant createdAt) {

    public static PatientBookingSummaryResponse of(Booking booking) {
        Slot slot = booking.getSlot();
        Session session = slot.getSession();
        return new PatientBookingSummaryResponse(
                booking.getId(),
                session.getClinic().getId(),
                session.getClinic().getName(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                booking.getAppointmentType().getName(),
                session.getMode(),
                session.getSessionDate(),
                slot.getStartTime(),
                slot.getTokenNumber(),
                booking.getStatus(),
                booking.getPaymentStatus(),
                booking.getLockedFee(),
                booking.getCreatedAt());
    }
}
