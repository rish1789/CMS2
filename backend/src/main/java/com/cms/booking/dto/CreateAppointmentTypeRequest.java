package com.cms.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateAppointmentTypeRequest(
        @Size(max = 100) String name, @DecimalMin(value = "0", message = "feeOverride must not be negative") BigDecimal feeOverride) {}
