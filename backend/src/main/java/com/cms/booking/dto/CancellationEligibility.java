package com.cms.booking.dto;

import com.cms.booking.domain.PatientCancellationRefusal;

/** 069-patient-visit-outcomes: advisory only - the cancel endpoint always re-checks (FR-005). */
public record CancellationEligibility(boolean allowed, PatientCancellationRefusal reason) {

    public static CancellationEligibility of(PatientCancellationRefusal refusal) {
        return new CancellationEligibility(refusal == null, refusal);
    }
}
