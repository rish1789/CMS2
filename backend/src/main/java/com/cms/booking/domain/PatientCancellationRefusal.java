package com.cms.booking.domain;

/** 069-patient-visit-outcomes: why a patient cannot self-cancel a booking (spec eligibility table). */
public enum PatientCancellationRefusal {
    ALREADY_CANCELLED,
    VISIT_RESOLVED,
    QUEUE_BOOKING,
    WALK_IN,
    CUTOFF_PASSED
}
