package com.cms.waitlist.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record StaffJoinWaitlistRequest(UUID patientAccountId, UUID doctorProfileId, @Size(max = 100) String specialization) {}
