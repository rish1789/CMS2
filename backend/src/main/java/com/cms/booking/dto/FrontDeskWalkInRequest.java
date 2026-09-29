package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * 063-front-desk-walk-in (contract section 1): either {@code patientId} (an existing clinic
 * patient) or {@code patientName} (a new one, with optional phone and email). {@code visitReason}
 * is a {@code VisitReason} name, validated by the service so an unknown value maps to
 * VISIT_REASON_REQUIRED rather than a generic JSON error.
 */
public record FrontDeskWalkInRequest(
        UUID sessionId,
        UUID patientId,
        @Size(max = 200) String patientName,
        @Size(max = 20) String patientPhone,
        @Size(max = 254) String patientEmail,
        UUID appointmentTypeId,
        String visitReason,
        String visitReasonDetail,
        boolean confirmDuplicate) {}
