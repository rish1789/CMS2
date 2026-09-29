package com.cms.booking.dto;

import java.util.UUID;

/**
 * real-bug-fix 2026-09-17: found live - a newly-onboarded doctor (Kamlesh Rawat) had zero
 * appointment types and no default fee configured, which staff never noticed until a patient
 * hit an empty "Appointment type" dropdown on the booking page. Human error, not a code bug -
 * StaffOnboardingService deliberately never creates a default AppointmentType/fee (Constitution
 * IV: no speculative data), so nothing enforces this gets configured before a doctor otherwise
 * looks fully staffed. This surfaces the gap on the staff-console Doctors page instead, so it's
 * caught by looking, not by a patient hitting a dead end first.
 *
 * <p>{@code hasAppointmentTypeMissingFeeOverride} - found live in the same testing pass: an
 * earlier version of this feature flagged every doctor with no default fee, including one
 * (Gauresh Kumar) whose every AppointmentType already carries its own {@code feeOverride} -
 * FeeResolutionService never needs the default fee in that case, so that doctor is fully
 * bookable despite having none. {@link #isBookingReady()} is accurate to what actually blocks a
 * booking: at least one AppointmentType exists, and every one of them can resolve a fee (its
 * own override, or the doctor's default fee as a fallback).
 */
public record DoctorBookingReadinessResponse(
        UUID doctorProfileId,
        boolean hasAppointmentTypes,
        boolean hasDefaultFee,
        boolean hasAppointmentTypeMissingFeeOverride) {

    /** Named isXxx (not the bare record-component convention) so Jackson serializes it as its own "bookingReady" field. */
    public boolean isBookingReady() {
        return hasAppointmentTypes && (!hasAppointmentTypeMissingFeeOverride || hasDefaultFee);
    }
}
