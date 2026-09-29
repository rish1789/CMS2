package com.cms.waitlist.service;

import com.cms.identity.admin.domain.ClinicRejectedEvent;
import com.cms.inbox.service.InboxItemService;
import com.cms.waitlist.domain.WaitlistEntry;
import com.cms.waitlist.domain.WaitlistEntryStatus;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 062-rejected-clinic-gating (FR-011): the waitlist module's consumer of {@link
 * ClinicRejectedEvent} - nobody keeps waiting for a clinic that can't serve them. Outstanding
 * offers' inbox items are resolved first (the same call a successful claim makes), then every
 * WAITING/OFFERED entry at the clinic is expired in one bulk update.
 *
 * <p>{@code REQUIRES_NEW}: this writes from an AFTER_COMMIT listener, where a REQUIRED transaction
 * would join the already-committed one and never persist (analyze finding C1).
 */
@Component
public class ClinicRejectionWaitlistListener {

    private static final Logger log = LoggerFactory.getLogger(ClinicRejectionWaitlistListener.class);

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final InboxItemService inboxItemService;

    public ClinicRejectionWaitlistListener(
            WaitlistEntryRepository waitlistEntryRepository, InboxItemService inboxItemService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.inboxItemService = inboxItemService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onClinicRejected(ClinicRejectedEvent event) {
        for (WaitlistEntry offered :
                waitlistEntryRepository.findByClinic_IdAndStatus(event.clinicId(), WaitlistEntryStatus.OFFERED)) {
            inboxItemService.resolveByWaitlistEntry(offered.getId());
        }
        int expired = waitlistEntryRepository.expireOpenByClinic(event.clinicId());
        log.info("Clinic rejection closed waitlist: clinicId={}, expiredEntries={}", event.clinicId(), expired);
    }
}
