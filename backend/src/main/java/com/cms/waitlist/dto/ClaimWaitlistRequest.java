package com.cms.waitlist.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ClaimWaitlistRequest(UUID appointmentTypeId, @Size(max = 200) String patientName) {}
