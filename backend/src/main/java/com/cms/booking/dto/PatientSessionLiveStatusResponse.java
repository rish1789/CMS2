package com.cms.booking.dto;

import com.cms.booking.domain.VisitOutcome;

import java.util.UUID;

/**
 * 061-doctor-live-status (data-model.md, contracts/doctor-live-status.md). A deliberately
 * narrower projection than {@code SessionLiveStatusResponse} (the staff/doctor shape) - {@code
 * statusText} is always plain language, never a raw status code (spec FR-004/FR-011), and no
 * other patient's data is ever present.
 */
public record PatientSessionLiveStatusResponse(
        UUID bookingId,
        boolean applicable,
        String doctorName,
        Integer currentPatientOrdinal,
        String statusText,
        Integer estimatedWaitMinutes,
        /** 069 FR-004: the patient's own visit outcome - always present, even when not applicable. */
        VisitOutcome visitOutcome) {}
