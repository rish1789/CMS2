package com.cms.booking.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PatientBookSlotRequest(@Size(max = 200) String patientName, UUID appointmentTypeId) {}
