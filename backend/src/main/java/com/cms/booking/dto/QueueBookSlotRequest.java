package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 022: staff-assisted queue booking request - same shape as {@link BookSlotRequest} (016). */
public record QueueBookSlotRequest(
        UUID patientId,
        @Size(max = 200) String patientName,
        @Size(max = 20) String patientPhone,
        UUID appointmentTypeId) {}
