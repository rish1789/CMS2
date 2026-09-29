package com.cms.booking.service;

import com.cms.booking.exception.SessionDeletionBlockedException;
import com.cms.booking.repository.BookingRepository;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.repository.SessionCancellationRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * real-bug-fix 2026-09-17: a Schedule edit is deliberately non-retroactive (ScheduleService's own
 * documented invariant) - correcting a wrong recurring schedule never touches Sessions already
 * generated from the old, wrong values. Until now there was no way to remove one of those stale
 * Sessions and let the next generation run rebuild it from the now-correct Schedule; {@code
 * SessionCancellationService.cancelSession} only cancels Bookings inside a Session, it never
 * deletes the Session/Slot rows themselves.
 *
 * <p>Deletion is gated the same way ClinicVerificationService.deleteGuarded gates a permanent
 * clinic delete: block outright the instant any real activity is attached, never cascade through
 * it. "Real activity" here is any Booking (any status, including a retained-not-deleted cancelled
 * one) or any waitlist offer (even a lapsed one) that ever referenced a Slot in this Session -
 * every other Slot lifecycle state (BOOKED/COMPLETED/NO_SHOW) is only reachable by way of a
 * Booking existing first, so this one check is a complete proxy for "has this Session ever really
 * been used."
 *
 * <p>065-phase1-stabilization (v41-design-review §8-H item 7): a session cancellation record is
 * real activity too. Deleting a cancelled session would lose the cancellation, and the nightly
 * generation run would recreate the session as bookable.
 */
@Service
public class SessionDeletionService {

    private final SessionRepository sessionRepository;
    private final SlotRepository slotRepository;
    private final BookingRepository bookingRepository;
    private final WaitlistEntryRepository waitlistEntryRepository;
    private final SessionCancellationRepository sessionCancellationRepository;

    public SessionDeletionService(
            SessionRepository sessionRepository,
            SlotRepository slotRepository,
            BookingRepository bookingRepository,
            WaitlistEntryRepository waitlistEntryRepository,
            SessionCancellationRepository sessionCancellationRepository) {
        this.sessionRepository = sessionRepository;
        this.slotRepository = slotRepository;
        this.bookingRepository = bookingRepository;
        this.waitlistEntryRepository = waitlistEntryRepository;
        this.sessionCancellationRepository = sessionCancellationRepository;
    }

    @Transactional
    public void deleteSession(Session session) {
        if (hasRealActivity(session)) {
            long bookingCount = bookingRepository.countBySlot_Session_Id(session.getId());
            long waitlistCount = waitlistEntryRepository.countByOfferedSlot_Session_Id(session.getId());
            long cancellationCount = sessionCancellationRepository.countBySession_Id(session.getId());
            throw new SessionDeletionBlockedException(String.format(
                    "Cannot delete: %d booking(s), %d waitlist offer(s), %d cancellation record(s) attached",
                    bookingCount, waitlistCount, cancellationCount));
        }

        slotRepository.deleteAll(slotRepository.findBySession_Id(session.getId()));
        sessionRepository.delete(session);
    }

    /**
     * 055-schedule-break-window: the same "has this Session ever really been used" check {@link
     * #deleteSession} throws on, exposed so {@link ScheduleDeletionService} can decide
     * delete-vs-detach per Session itself, inside its own single transaction - calling this
     * class's own {@code @Transactional deleteSession} from inside another {@code @Transactional}
     * method and catching the exception does NOT work (found live testing the schedule-merge
     * flow): with the default REQUIRED propagation the two share one physical transaction, and
     * Spring's AOP proxy marks it rollback-only the instant the exception is thrown - regardless
     * of the caller catching it - so the outer transaction fails to commit with
     * UnexpectedRollbackException even though the caller "handled" it.
     */
    public boolean hasRealActivity(Session session) {
        long bookingCount = bookingRepository.countBySlot_Session_Id(session.getId());
        long waitlistCount = waitlistEntryRepository.countByOfferedSlot_Session_Id(session.getId());
        long cancellationCount = sessionCancellationRepository.countBySession_Id(session.getId());
        return bookingCount > 0 || waitlistCount > 0 || cancellationCount > 0;
    }
}
