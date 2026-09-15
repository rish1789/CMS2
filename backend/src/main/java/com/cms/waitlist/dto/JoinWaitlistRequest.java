package com.cms.waitlist.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public record JoinWaitlistRequest(UUID doctorProfileId, @Size(max = 100) String specialization) {}
