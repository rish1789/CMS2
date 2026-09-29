package com.cms.scheduling.domain;



public enum SlotStatus {
    OPEN,
    /** 020-staff-assisted-fixed-time-booking: set once a Booking is created for this Slot. */
    BOOKED,
    /** 023-no-show-detection: set by the automatic sweep once a BOOKED Fixed-Time Slot's grace period elapses unattended. */
    NO_SHOW,
    /**
     * 057-day-sheet-status-overhaul: reachable from BOOKED (patient arrived) or NO_SHOW (a
     * mistaken auto-No-Show, corrected). Removes a Slot from No-Show sweep eligibility and
     * makes it eligible for automatic completion once its scheduled end time passes.
     */
    APPEARED,
    /** 026-session-delay-tracking: reachable from BOOKED (unchanged direct path) or APPEARED (057) - a terminal, one-way transition. */
    COMPLETED
}
