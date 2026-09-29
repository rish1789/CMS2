package com.cms.booking.dto;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.domain.VisitReason;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.SessionCancellation;
import com.cms.scheduling.domain.Slot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * 041-staff-console-pickers FR-004/FR-005: the day sheet's per-session slot+booking detail.
 *
 * <p>042-day-sheet-hardening FR-010: {@code doctorName}/{@code sessionDate} are populated from
 * data already loaded for this same request (the Session's own doctorProfile/account
 * association) - zero new queries - so the frontend header no longer depends on React Router
 * navigation state, which is lost on a page refresh or a direct/bookmarked link.
 *
 * <p>065-phase1-stabilization (owner decision 3): additive {@code cancelled} (a whole-session
 * cancellation exists) and {@code cancelledRanges} (each recorded {@code [fromTime, toTime)}, a null
 * {@code toTime} meaning to the end of the session), so staff see why a slot can't be booked.
 */
public record SessionDaySheetResponse(
        UUID sessionId,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        String mode,
        List<SlotDetail> slots,
        boolean cancelled,
        List<CancelledRange> cancelledRanges) {

    public static SessionDaySheetResponse of(
            Session session, List<SlotDetail> slots, List<SessionCancellation> cancellations) {
        return new SessionDaySheetResponse(
                session.getId(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getSessionDate(),
                session.getMode().name(),
                slots,
                cancellations.stream().anyMatch(c -> c.getFromTime() == null),
                cancellations.stream()
                        .filter(c -> c.getFromTime() != null)
                        .sorted(Comparator.comparing(SessionCancellation::getFromTime))
                        .map(c -> new CancelledRange(c.getFromTime(), c.getToTime()))
                        .toList());
    }

    public record CancelledRange(LocalTime fromTime, LocalTime toTime) {}

    public record SlotDetail(
            UUID slotId,
            LocalTime startTime,
            LocalTime endTime,
            Integer tokenNumber,
            String status,
            // 063-front-desk-walk-in (FR-014): when the patient was sent in, and when the visit finished.
            Instant appearedAt,
            Instant completedAt,
            BookingDetail booking) {

        public static SlotDetail of(Slot slot, BookingDetail booking) {
            return new SlotDetail(
                    slot.getId(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getTokenNumber(),
                    slot.getStatus().name(),
                    slot.getAppearedAt(),
                    slot.getCompletedAt(),
                    booking);
        }
    }

    /** 063-front-desk-walk-in (FR-019): {@code visitReason}/{@code visitReasonDetail} are null for booked visits. */
    public record BookingDetail(
            UUID bookingId,
            UUID patientId,
            String patientName,
            boolean isWalkIn,
            VisitReason visitReason,
            String visitReasonDetail) {

        public static BookingDetail from(Booking booking) {
            return new BookingDetail(
                    booking.getId(),
                    booking.getPatient().getId(),
                    booking.getPatient().getName(),
                    booking.getSource() == BookingSource.WALK_IN,
                    booking.getVisitReason(),
                    booking.getVisitReasonDetail());
        }
    }
}
