package com.cms.scheduling.service;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.exception.NotAFixedTimeSessionException;
import com.cms.scheduling.exception.NotAQueueSessionException;
import com.cms.scheduling.exception.SessionNotFoundException;
import com.cms.scheduling.exception.TokenIssuanceFailedException;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;


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
        return issueWithRetry(sessionId, ScheduleMode.QUEUE);
    }

    /**
     * 063-front-desk-walk-in (research.md Decision 1): the next place in a Fixed-Time session's
     * walk-in line (W1, W2...) - an untimed token Slot from the very same per-session counter and
     * unique index as a Queue session's tokens, so the walk-in line is not a second queue system.
     * A Fixed-Time session's timed Slots carry no token, so its walk-ins number from 1.
     */
    public Slot issueNextWalkInSlot(UUID sessionId) {
        return issueWithRetry(sessionId, ScheduleMode.FIXED_TIME);
    }

    private Slot issueWithRetry(UUID sessionId, ScheduleMode requiredMode) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            try {
                return attemptIssueSlot(sessionId, requiredMode);
            } catch (DataIntegrityViolationException e) {
                // Lost the race for this token number; retry with a freshly-read max.
            }
        }
        throw new TokenIssuanceFailedException(sessionId);
    }

    @Transactional
    Slot attemptIssueSlot(UUID sessionId, ScheduleMode requiredMode) {
        Session session = sessionRepository.findById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
        if (session.getMode() != requiredMode) {
            throw requiredMode == ScheduleMode.QUEUE
                    ? new NotAQueueSessionException(sessionId)
                    : new NotAFixedTimeSessionException(sessionId);
        }

        int nextToken = slotRepository.findMaxTokenNumberBySession_Id(sessionId).orElse(0) + 1;
        Slot token = new Slot(session, nextToken);
        // 064-queue-send-in-complete (FR-001, research.md Decision 1): a token is only ever issued to
        // the patient being booked, so it starts life waiting (BOOKED) - the state the existing
        // Appeared/Complete actions and queue position work from. Set here, at the single
        // issuance point, rather than in each booking path (they hold a detached Slot).
        token.setStatus(SlotStatus.BOOKED);
        return slotRepository.save(token);
    }
}
