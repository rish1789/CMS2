package com.cms.scheduling;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 019: issues one new, never-reused-token {@link Slot} per call for a Queue/Token
 * {@link Session}. No automatic trigger - 018 (unbuilt) is this feature's only real
 * caller (spec Assumptions).
 */
@Service
public class QueueSlotService {

    private static final int MAX_ATTEMPTS = 5;

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;

    public QueueSlotService(SessionRepository sessionRepository, SlotRepository slotRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
    }

    /**
     * Deliberately NOT {@code @Transactional}: each attempt runs in its own, fresh
     * transaction via {@link #attemptIssueSlot}, mirroring 011's proven pattern - a lost
     * race on one attempt never poisons a transaction the next attempt could otherwise
     * use, and every caller here has an equally legitimate claim to a new token (unlike
     * 011's own "whoever commits first wins, skip the rest" race).
     */
    public Slot issueNextSlot(UUID sessionId) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return attemptIssueSlot(sessionId);
            } catch (DataIntegrityViolationException e) {
                // Lost the race for this token number; retry with a freshly-read max.
            }
        }
        throw new TokenIssuanceFailedException(sessionId);
    }

    @Transactional
    Slot attemptIssueSlot(UUID sessionId) {
        Session session = sessionRepository.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getMode() != ScheduleMode.QUEUE) {
            throw new NotAQueueSessionException(sessionId);
        }

        int nextToken = slotRepository.findMaxTokenNumberBySession_Id(sessionId).orElse(0) + 1;
        return slotRepository.save(new Slot(session, nextToken));
    }
}
