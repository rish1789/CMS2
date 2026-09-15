package com.cms.booking;

import com.cms.scheduling.NotAFixedTimeSessionException;
import com.cms.scheduling.ScheduleMode;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotRepository;
import com.cms.scheduling.SlotStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 028: the shared, concurrency-critical core both {@link StaffBookingCancellationController}
 * and {@link PatientBookingCancellationController} call - no authorization/eligibility here,
 * that's each caller-side controller's own job (research.md R5), mirroring 027's
 * {@code QueuePositionService.positionOf(Booking)} shape. Takes an already-fetched
 * {@link Booking} so exactly one lookup happens per call, not two.
 *
 * <p>033-deverification-cascade-auto-cancel research.md: {@code cancel} uses
 * {@code Propagation.REQUIRES_NEW}, not the default REQUIRED - it's called from within
 * {@code DeVerificationCascadeService}'s own batch loop, itself already transactional. A lost
 * race here (a normal, expected outcome for one booking in a large batch) throws
 * {@link BookingNotCancellableException}; without REQUIRES_NEW, that exception propagating out
 * of this *participating* transactional boundary would mark the cascade's *entire shared*
 * transaction rollback-only - silently discarding every other successfully-cancelled booking in
 * the same batch once the cascade's own method returns and its transaction tries to commit, even
 * though the cascade's own catch block never re-throws. REQUIRES_NEW gives every call here its
 * own independent, isolated transaction instead. Behaviorally identical for this method's other
 * two callers ({@link StaffBookingCancellationController}/{@link PatientBookingCancellationController}),
 * both plain, non-transactional controllers with no ambient transaction to either join or
 * suspend.
 */
@Service
public class BookingCancellationService {

    private final BookingRepository bookingRepository;
    private final SlotRepository slotRepository;
    private final ApplicationEventPublisher eventPublisher;

    public BookingCancellationService(
            BookingRepository bookingRepository, SlotRepository slotRepository, ApplicationEventPublisher eventPublisher) {
        this.bookingRepository = bookingRepository;
        this.slotRepository = slotRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking cancel(Booking booking) {
        return doCancel(booking, null, null);
    }

    /**
     * patient-cancellation-reason: the reason-carrying sibling of {@link #cancel(Booking)} - the
     * patient self-service path calls this one, every other caller keeps calling the plain
     * no-reason overload above unchanged.
     *
     * <p>Deliberately its own independently-annotated public method delegating to a private
     * {@code doCancel} helper, not one overload calling the other via {@code this.cancel(...)} -
     * a same-class self-invocation bypasses Spring's transactional proxy entirely (see this
     * class's own javadoc: this codebase has hit that exact bug twice already), which would
     * silently make the *other* overload stop actually getting its own REQUIRES_NEW transaction
     * whenever called through the first. Both public methods stay real, separately-proxied entry
     * points; only the shared, non-transactional logic is factored out.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Booking cancel(Booking booking, BookingCancellationReason reason, String reasonDetail) {
        return doCancel(booking, reason, reasonDetail);
    }

    private Booking doCancel(Booking booking, BookingCancellationReason reason, String reasonDetail) {
        Slot slot = booking.getSlot();

        // spec FR-009: Fixed-Time-only, mirroring 025/026/027's identical reused exception.
        if (slot.getSession().getMode() != ScheduleMode.FIXED_TIME) {
            throw new NotAFixedTimeSessionException(slot.getSession().getId());
        }

        // FR-003 fast path: a clear rejection in the common (non-racing) case. The actual
        // guarantee is cancelIfActive's data-layer-guarded conditional update below
        // (research.md R3) - this check alone is not what FR-008/SC-005 rely on.
        if (slot.getStatus() != SlotStatus.BOOKED) {
            throw new BookingNotCancellableException(booking.getId());
        }

        int updated = bookingRepository.cancelIfActive(booking.getId(), reason, reasonDetail);
        if (updated == 0) {
            // Already cancelled, or a concurrent caller just won the race.
            throw new BookingNotCancellableException(booking.getId());
        }

        // Keeps this in-memory entity consistent with cancelIfActive's DB-level change, which
        // bypassed the persistence context (see Booking.setStatus's own doc).
        booking.setStatus(BookingStatus.CANCELLED);
        booking.recordCancellationReason(reason, reasonDetail);

        // _diagnostics CRITICAL - [BOOKING] - [DETACHED_ENTITY_LOST_UPDATE]: booking (and its
        // eagerly-loaded slot) was fetched by the caller-side controller, outside any
        // transaction of this service's own - by the time execution reaches this REQUIRES_NEW
        // method, both are detached from that now-closed persistence context. A bare
        // slot.setStatus(...) on a detached entity is silently never persisted - no dirty
        // checking runs for an entity this transaction's EntityManager never loaded or attached.
        // An explicit save() is what actually schedules the UPDATE (found live: the booking
        // correctly cancelled every time, but the slot stayed BOOKED forever afterward,
        // permanently blocking it from ever being rebooked by anyone).
        slot.setStatus(SlotStatus.OPEN);
        slotRepository.save(slot);

        // FR-005/SC-003: only reachable once the transition above is confirmed real - never
        // on a lost race, never on an already-cancelled Booking.
        eventPublisher.publishEvent(BookingCancelledEvent.of(booking.getId(), slot.getId()));

        return booking;
    }
}
