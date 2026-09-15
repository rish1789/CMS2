package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

/** 022: patient self-service queue booking request - same shape as {@link PatientBookSlotRequest} (021). */
public record PatientQueueBookSlotRequest(@Size(max = 200) String patientName, UUID appointmentTypeId) {}
