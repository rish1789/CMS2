package com.cms.booking.dto;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.VisitReason;
import com.cms.booking.service.FrontDeskWalkInService;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 063-front-desk-walk-in (contract section 1, FR-018): where the walk-in landed. {@code tokenNumber}
 * is the W-number in a Fixed-Time walk-in line or the token in a Queue session; {@code
 * walkInPosition} is the place in a Fixed-Time walk-in line and null for a Queue token.
 */
public record FrontDeskWalkInResponse(
        UUID bookingId,
        UUID slotId,
        UUID sessionId,
        String mode,
        Integer tokenNumber,
        Integer walkInPosition,
        UUID patientId,
        String patientName,
        String doctorName,
        UUID appointmentTypeId,
        BigDecimal lockedFee,
        VisitReason visitReason,
        String visitReasonDetail) {

    public static FrontDeskWalkInResponse of(FrontDeskWalkInService.Registration registration) {
        Booking booking = registration.booking();
        return new FrontDeskWalkInResponse(
                booking.getId(),
                booking.getSlot().getId(),
                booking.getSlot().getSession().getId(),
                booking.getSlot().getSession().getMode().name(),
                booking.getSlot().getTokenNumber(),
                registration.walkInPosition(),
                booking.getPatient().getId(),
                booking.getPatient().getName(),
                booking.getSlot().getSession().getDoctorProfile().getAccount().getName(),
                booking.getAppointmentType().getId(),
                booking.getLockedFee(),
                booking.getVisitReason(),
                booking.getVisitReasonDetail());
    }
}
