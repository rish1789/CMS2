package com.cms.scheduling;

public enum SlotStatus {
    OPEN,
    /** 020-staff-assisted-fixed-time-booking: set once a Booking is created for this Slot. */
    BOOKED,
    /** 023-no-show-detection: set by the automatic sweep once a BOOKED Fixed-Time Slot's grace period elapses unattended. */
    NO_SHOW,
    /** 026-session-delay-tracking: set by staff, reachable only from BOOKED - a terminal, one-way transition. */
    COMPLETED
}
