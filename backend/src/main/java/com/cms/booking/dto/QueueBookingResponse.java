package com.cms.booking.dto;

import com.cms.booking.Booking;
import com.cms.booking.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** 022: BookingResponse's fields plus tokenNumber - fulfills the user story's "receive a token number" (data-model.md). */
public record QueueBookingResponse(
        UUID id,
        UUID slotId,
        int tokenNumber,
        UUID patientId,
        UUID appointmentTypeId,
        BigDecimal lockedFee,
        PaymentStatus paymentStatus,
        Instant createdAt) {

    public static QueueBookingResponse of(Booking booking) {
        return new QueueBookingResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getSlot().getTokenNumber(),
                booking.getPatient().getId(),
                booking.getAppointmentType().getId(),
                booking.getLockedFee(),
                booking.getPaymentStatus(),
                booking.getCreatedAt());
    }
}
