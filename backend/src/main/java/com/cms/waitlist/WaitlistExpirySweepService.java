package com.cms.waitlist;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 032 FR-007: the automatic-expiry counterpart to an explicit decline - finds every currently
 * lapsed {@code OFFERED} entry and releases each via the same shared core
 * ({@link WaitlistReleaseService}), mirroring {@code NoShowDetectionService}'s (023) own
 * service/trigger split.
 */
@Service
public class WaitlistExpirySweepService {

    private final WaitlistEntryRepository waitlistEntryRepository;
    private final WaitlistReleaseService waitlistReleaseService;

    public WaitlistExpirySweepService(
            WaitlistEntryRepository waitlistEntryRepository, WaitlistReleaseService waitlistReleaseService) {
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.waitlistReleaseService = waitlistReleaseService;
    }

    /** Returns the number of entries released. A freshly re-offered entry always has a non-lapsed window (research.md R5), so it can never spuriously qualify within the same pass. */
    public int sweepExpiredOffers() {
        List<WaitlistEntry> lapsed =
                waitlistEntryRepository.findByStatusAndOfferExpiresAtBefore(WaitlistEntryStatus.OFFERED, Instant.now());
        for (WaitlistEntry entry : lapsed) {
            waitlistReleaseService.release(entry);
        }
        return lapsed.size();
    }
}
