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
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.util.UUID;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 019: issues one new, never-reused-token {@link Slot} per call for a Queue/Token
 * {@link Session}.
 *
 * <p>067-queue-token-issuance-race (research.md Decisions 1, 4, 5): issuance locks the Session row
 * before reading {@code max(token) + 1}, so concurrent callers for one session take turns instead
 * of colliding - no retry loop, and numbers stay 1..N with no gaps. The lock and the token join the
 * caller's transaction and are held until it ends, so a booking or walk-in that fails afterwards
 * rolls its token back too (FR-008); called without a transaction, each call is its own. Different
 * sessions never wait on each other. A caller holding this lock must not open a second database
 * connection before it commits (no {@code REQUIRES_NEW}). A wait longer than
 * {@link #LOCK_TIMEOUT} is refused as {@link TokenIssuanceFailedException} (503, retry later).
 */
@Service
public class QueueSlotService {

    static final String LOCK_TIMEOUT = "5s";

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final EntityManager entityManager;

    public QueueSlotService(SessionRepository sessionRepository, SlotRepository slotRepository, EntityManager entityManager) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public Slot issueNextSlot(UUID sessionId) {
        return issue(sessionId, ScheduleMode.QUEUE);
    }

    /**
     * 063-front-desk-walk-in (research.md Decision 1): the next place in a Fixed-Time session's
     * walk-in line (W1, W2...) - an untimed token Slot from the very same per-session counter and
     * unique index as a Queue session's tokens, so the walk-in line is not a second queue system.
     * A Fixed-Time session's timed Slots carry no token, so its walk-ins number from 1.
     */
    @Transactional
    public Slot issueNextWalkInSlot(UUID sessionId) {
        return issue(sessionId, ScheduleMode.FIXED_TIME);
    }

    private Slot issue(UUID sessionId, ScheduleMode requiredMode) {
        Session session = lockSession(sessionId);
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

    private Session lockSession(UUID sessionId) {
        // SET LOCAL: the bound applies to this transaction only (research.md Decision 5).
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'").executeUpdate();
        try {
            return sessionRepository.findWithLockById(sessionId).orElseThrow(() -> new SessionNotFoundException(sessionId));
        } catch (PessimisticLockingFailureException | PessimisticLockException | LockTimeoutException e) {
            throw new TokenIssuanceFailedException(sessionId);
        }
    }
}
