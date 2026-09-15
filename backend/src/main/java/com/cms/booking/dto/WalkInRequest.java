package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 025: {@code overrideReason} is only actually required when the system determines a priority-(3) insertion is the only available path - the client can't know this in advance, so it's always optional on the wire. */
public record WalkInRequest(
        UUID patientId,
        @Size(max = 200) String patientName,
        @Size(max = 20) String patientPhone,
        UUID appointmentTypeId,
        @Size(max = 1000) String overrideReason) {}
