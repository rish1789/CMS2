package com.cms.booking;

/** patient-cancellation-reason: why a patient cancelled their own Booking - collected by {@code PatientBookingCancellationController}, never required of a staff-initiated cancellation. */
public enum BookingCancellationReason {
    SCHEDULE_CONFLICT,
    FEELING_BETTER,
    FOUND_ANOTHER_PROVIDER,
    PERSONAL_EMERGENCY,
    OTHER
}
