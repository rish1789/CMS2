package com.cms.booking;

import com.cms.identity.admin.ClinicDeVerifiedEvent;
import com.cms.identity.admin.DoctorLicenseRevokedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 033: the sole consumer of {@link ClinicDeVerifiedEvent} (003, unconsumed until now) and
 * {@link DoctorLicenseRevokedEvent} (new). AFTER_COMMIT so this never reacts to a
 * de-verification/revoke that was ultimately rolled back, mirroring 031's
 * {@code WaitlistBumpListener} and every other cross-module listener in this codebase.
 */
@Component
public class DeVerificationCascadeListener {

    private final DeVerificationCascadeService deVerificationCascadeService;

    public DeVerificationCascadeListener(DeVerificationCascadeService deVerificationCascadeService) {
        this.deVerificationCascadeService = deVerificationCascadeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClinicDeVerified(ClinicDeVerifiedEvent event) {
        deVerificationCascadeService.cascadeFromClinic(event.clinicId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDoctorLicenseRevoked(DoctorLicenseRevokedEvent event) {
        deVerificationCascadeService.cascadeFromDoctor(event.doctorProfileId());
    }
}
