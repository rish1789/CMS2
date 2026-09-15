package com.cms.booking.dto;

import com.cms.booking.Booking;
import com.cms.booking.BookingStatus;
import com.cms.booking.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID slotId,
        UUID patientId,
        UUID appointmentTypeId,
        BigDecimal lockedFee,
        PaymentStatus paymentStatus,
        BookingStatus status,
        Instant createdAt) {

    public static BookingResponse of(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getPatient().getId(),
                booking.getAppointmentType().getId(),
                booking.getLockedFee(),
                booking.getPaymentStatus(),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
