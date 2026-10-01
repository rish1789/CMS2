package com.cms.booking.exception;

/** 068-per-clinic-fees: the amount is missing, negative, has more than 2 decimals or does not fit NUMERIC(10,2). */
public class InvalidFeeAmountException extends RuntimeException {

    public InvalidFeeAmountException() {
        super("amount must be a non-negative number with at most 2 decimals, below 100000000");
    }
}
