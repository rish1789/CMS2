package com.cms.clinical.exception;



/** FR-006: a Prescription must have at least one Item. */
public class PrescriptionItemRequiredException extends RuntimeException {

    public PrescriptionItemRequiredException() {
        super("A prescription requires at least one item");
    }
}
