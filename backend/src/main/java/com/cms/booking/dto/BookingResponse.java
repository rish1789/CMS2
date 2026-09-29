package com.cms.booking.dto;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
        UUID id,
        UUID slotId,
        UUID patientId,
        String patientName,
        String doctorName,
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
                booking.getPatient().getName(),
                booking.getSlot().getSession().getDoctorProfile().getAccount().getName(),
                booking.getAppointmentType().getId(),
                booking.getLockedFee(),
                booking.getPaymentStatus(),
                booking.getStatus(),
                booking.getCreatedAt());
    }
}
