package com.cms.booking.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SetDefaultFeeRequest(
        @NotNull @DecimalMin(value = "0", message = "amount must not be negative") BigDecimal amount) {}
