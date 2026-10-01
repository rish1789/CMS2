package com.cms.booking.dto;

import java.math.BigDecimal;

/** 068-per-clinic-fees: the amount is validated by ClinicFeeService so a bad one maps to INVALID_FEE_AMOUNT. */
public record SetClinicFeeRequest(BigDecimal amount) {}
