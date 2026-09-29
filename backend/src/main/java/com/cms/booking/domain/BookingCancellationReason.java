package com.cms.booking.domain;



/** patient-cancellation-reason: why a patient cancelled their own Booking - collected by {@code PatientBookingCancellationController}, never required of a staff-initiated cancellation. */
public enum BookingCancellationReason {
    SCHEDULE_CONFLICT,
    FEELING_BETTER,
    FOUND_ANOTHER_PROVIDER,
    PERSONAL_EMERGENCY,
    OTHER,
    /**
     * 062-rejected-clinic-gating: system-only - set solely by the rejection cascade when a Super
     * Admin rejects the clinic. Never accepted as caller input (the patient cancel endpoint refuses
     * it); lets the patient console explain why the booking was cancelled.
     */
    CLINIC_REJECTED
}
