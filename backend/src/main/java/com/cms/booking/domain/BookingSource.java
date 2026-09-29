package com.cms.booking.domain;

public enum BookingSource {
    SCHEDULED,
    /** 025-walk-in-priority-insertion: set only by {@code WalkInInsertionService}. */
    WALK_IN
}
