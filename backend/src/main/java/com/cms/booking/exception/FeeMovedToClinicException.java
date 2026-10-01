package com.cms.booking.exception;

/**
 * 068-per-clinic-fees FR-012: a doctor-wide price write. Prices are now set per clinic by that
 * clinic's admin. {@code gone} marks the retired default-fee endpoint itself (410) rather than a
 * fee sent along with an otherwise valid request (400).
 */
public class FeeMovedToClinicException extends RuntimeException {

    private final boolean gone;

    private FeeMovedToClinicException(boolean gone) {
        super("Fees are set per clinic: PUT /api/v1/clinics/{clinicId}/doctors/{doctorProfileId}/fees/...");
        this.gone = gone;
    }

    public static FeeMovedToClinicException endpointRetired() {
        return new FeeMovedToClinicException(true);
    }

    public static FeeMovedToClinicException feeNotAccepted() {
        return new FeeMovedToClinicException(false);
    }

    public boolean isGone() {
        return gone;
    }
}
