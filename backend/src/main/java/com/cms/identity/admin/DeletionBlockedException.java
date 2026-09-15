package com.cms.identity.admin;

/**
 * Super Admin console redesign: permanent delete refuses to cascade through real operational
 * data (a Schedule, Session, Patient, AppointmentType, etc.) - unlike Reject, this is
 * irreversible, and that data has its own DPDP-retention lifecycle (034) rather than being
 * something a "remove a fraudulent request" cleanup action should silently eat.
 */
public class DeletionBlockedException extends RuntimeException {

    public DeletionBlockedException(String message) {
        super(message);
    }
}
