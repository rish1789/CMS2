package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record BookSlotRequest(
        UUID patientId,
        @Size(max = 200) String patientName,
        @Size(max = 20) String patientPhone,
        UUID appointmentTypeId) {}
