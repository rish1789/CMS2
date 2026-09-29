package com.cms.patient.account.exception;



public class InvalidMobileNumberException extends RuntimeException {

    public InvalidMobileNumberException() {
        super("Mobile number does not match the Indian numbering plan");
    }
}
