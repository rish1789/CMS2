package com.cms.protection.dto;

import com.cms.booking.domain.Booking;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 060-booking-abuse-prevention (contracts/booking-protection.md #2, spec.md FR-021/FR-022):
 * the clinic-scoped evidence a ClinicAdmin sees when reviewing one flag - this clinic's own
 * bookings/cancellations/no-shows/rate-limit history only, plus the one documented cross-clinic
 * exception (a bare fact, never another clinic's detail).
 */
public record RecentActivityResponse(
        List<BookingSummary> recentBookings,
        List<BookingSummary> recentCancellations,
        List<BookingSummary> recentNoShows,
        List<RateLimitViolation> rateLimitViolations,
        long globalActiveAppointmentCount,
        boolean atGlobalLimit) {

    public record BookingSummary(UUID bookingId, LocalDate sessionDate, String doctorName, String status) {
        public static BookingSummary of(Booking booking) {
            return new BookingSummary(
                    booking.getId(),
                    booking.getSlot().getSession().getSessionDate(),
                    booking.getSlot().getSession().getDoctorProfile().getAccount().getName(),
                    booking.getStatus().name());
        }
    }

    public record RateLimitViolation(Instant occurredAt) {}
}
