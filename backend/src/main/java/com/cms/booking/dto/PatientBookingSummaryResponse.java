package com.cms.booking.dto;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancellationReason;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.VisitOutcome;
import com.cms.booking.domain.PaymentStatus;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
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
        Instant createdAt,
        /** 062-rejected-clinic-gating (FR-010): lets the patient console explain a CLINIC_REJECTED cancellation. Null unless a reason was recorded. */
        BookingCancellationReason cancellationReason,
        /** 069 FR-001: the patient's own visit outcome - not the booking state, not the session's progress. */
        VisitOutcome visitOutcome,
        /** 069 FR-002: advisory self-cancellation eligibility; the cancel endpoint always re-checks. */
        CancellationEligibility cancellation) {

    public static PatientBookingSummaryResponse of(
            Booking booking, VisitOutcome visitOutcome, CancellationEligibility cancellation) {
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
                booking.getCreatedAt(),
                booking.getCancellationReason(),
                visitOutcome,
                cancellation);
    }
}
