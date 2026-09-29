package com.cms.booking.domain;

/**
 * 063-front-desk-walk-in (FR-004): why a walk-in patient came in. Required for every front-desk
 * walk-in; {@link #OTHER} additionally requires free-text detail (enforced by the service and by the
 * V39 check constraint). Null on booked visits, which don't collect a reason.
 */
public enum VisitReason {
    FEVER_COLD_COUGH,
    PAIN,
    FOLLOW_UP,
    TEST_REPORT_REVIEW,
    PRESCRIPTION_REFILL,
    INJURY,
    GENERAL_CHECKUP,
    OTHER
}
