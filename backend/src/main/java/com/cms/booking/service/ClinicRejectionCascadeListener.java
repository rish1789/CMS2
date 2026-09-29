package com.cms.booking.service;

import com.cms.identity.admin.domain.ClinicRejectedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 062-rejected-clinic-gating: the booking module's consumer of {@link ClinicRejectedEvent}.
 * AFTER_COMMIT so it never reacts to a rejection that was ultimately rolled back - mirrors {@link
 * DeVerificationCascadeListener} exactly.
 */
@Component
public class ClinicRejectionCascadeListener {

    private final ClinicRejectionCascadeService clinicRejectionCascadeService;

    public ClinicRejectionCascadeListener(ClinicRejectionCascadeService clinicRejectionCascadeService) {
        this.clinicRejectionCascadeService = clinicRejectionCascadeService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onClinicRejected(ClinicRejectedEvent event) {
        clinicRejectionCascadeService.cascadeFromClinic(event.clinicId());
    }
}
