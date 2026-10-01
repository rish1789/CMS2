package com.cms.booking.domain;

/**
 * 069-patient-visit-outcomes (spec "Decision table"): the patient's own visit outcome, derived on
 * read from booking state, own slot status and the operational date - never stored. Kept separate
 * from {@link BookingStatus} (cancellation only) and from whole-session progress (061).
 */
public enum VisitOutcome {
    /** Booked, on today or a later operational day - including today after its start time (delayed). */
    SCHEDULED,
    /** Marked Appeared (057) today. */
    CHECKED_IN,
    COMPLETED,
    NO_SHOW,
    CANCELLED,
    /** Still Booked/Appeared on an earlier day - nothing was recorded; never shown as completed or missed. */
    NOT_RECORDED;

    /** A next-visit candidate (spec "Next-visit selection"). */
    public boolean isUpcoming() {
        return this == SCHEDULED || this == CHECKED_IN;
    }
}
