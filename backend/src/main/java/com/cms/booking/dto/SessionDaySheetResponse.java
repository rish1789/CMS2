package com.cms.booking.dto;

import com.cms.booking.Booking;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/**
 * 041-staff-console-pickers FR-004/FR-005: the day sheet's per-session slot+booking detail.
 *
 * <p>042-day-sheet-hardening FR-010: {@code doctorName}/{@code sessionDate} are populated from
 * data already loaded for this same request (the Session's own doctorProfile/account
 * association) - zero new queries - so the frontend header no longer depends on React Router
 * navigation state, which is lost on a page refresh or a direct/bookmarked link.
 */
public record SessionDaySheetResponse(
        UUID sessionId,
        UUID doctorProfileId,
        String doctorName,
        LocalDate sessionDate,
        String mode,
        List<SlotDetail> slots) {

    public static SessionDaySheetResponse of(Session session, List<SlotDetail> slots) {
        return new SessionDaySheetResponse(
                session.getId(),
                session.getDoctorProfile().getId(),
                session.getDoctorProfile().getAccount().getName(),
                session.getSessionDate(),
                session.getMode().name(),
                slots);
    }

    public record SlotDetail(
            UUID slotId,
            LocalTime startTime,
            LocalTime endTime,
            Integer tokenNumber,
            String status,
            boolean isBuffer,
            BookingDetail booking) {

        public static SlotDetail of(Slot slot, BookingDetail booking) {
            return new SlotDetail(
                    slot.getId(),
                    slot.getStartTime(),
                    slot.getEndTime(),
                    slot.getTokenNumber(),
                    slot.getStatus().name(),
                    slot.isBuffer(),
                    booking);
        }
    }

    public record BookingDetail(UUID bookingId, UUID patientId, String patientName) {

        public static BookingDetail from(Booking booking) {
            return new BookingDetail(booking.getId(), booking.getPatient().getId(), booking.getPatient().getName());
        }
    }
}
