package com.cms.booking.service;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.PatientCancellationRefusal;
import com.cms.booking.domain.VisitOutcome;
import com.cms.booking.dto.CancellationEligibility;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 069-patient-visit-outcomes (spec "Decision table"): the patient's own visit outcome and
 * self-cancellation eligibility, read-only. The cancel endpoint enforces the same rules through
 * {@link #requestRefusal}, so what the patient is shown and what is enforced cannot drift apart.
 * "Now" is the server's operational clock (065 FR-014, IST since #29).
 */
@Component
public class PatientVisitOutcomes {

    /** 028 FR-002: a patient may not self-cancel within 2 hours of the scheduled start. */
    public static final int CUTOFF_HOURS = 2;

    private final Supplier<LocalDateTime> now;

    @Autowired
    public PatientVisitOutcomes(SessionAvailabilityService sessionAvailabilityService) {
        this(sessionAvailabilityService::now);
    }

    /** For deterministic unit tests. */
    public PatientVisitOutcomes(Supplier<LocalDateTime> now) {
        this.now = now;
    }

    public VisitOutcome outcome(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return VisitOutcome.CANCELLED;
        }
        Slot slot = booking.getSlot();
        if (slot.getStatus() == SlotStatus.COMPLETED) {
            return VisitOutcome.COMPLETED;
        }
        if (slot.getStatus() == SlotStatus.NO_SHOW) {
            return VisitOutcome.NO_SHOW;
        }
        LocalDate today = now.get().toLocalDate();
        if (slot.getSession().getSessionDate().isBefore(today)) {
            return VisitOutcome.NOT_RECORDED;
        }
        return slot.getStatus() == SlotStatus.APPEARED ? VisitOutcome.CHECKED_IN : VisitOutcome.SCHEDULED;
    }

    /** Display order (spec eligibility table): a resolved outcome explains more than an elapsed cutoff. */
    public CancellationEligibility eligibility(Booking booking) {
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return CancellationEligibility.of(PatientCancellationRefusal.ALREADY_CANCELLED);
        }
        SlotStatus slotStatus = booking.getSlot().getStatus();
        if (slotStatus == SlotStatus.NO_SHOW || slotStatus == SlotStatus.COMPLETED) {
            return CancellationEligibility.of(PatientCancellationRefusal.VISIT_RESOLVED);
        }
        return CancellationEligibility.of(requestRefusal(booking.getSlot()).orElse(null));
    }

    /**
     * The cancel endpoint's own checks, in its existing order (028/063): Queue-mode first (its
     * start time is null), then an untimed walk-in, then the cutoff. Booking and slot state are
     * still enforced afterwards by {@link BookingCancellationService}.
     */
    public Optional<PatientCancellationRefusal> requestRefusal(Slot slot) {
        if (slot.getSession().getMode() != ScheduleMode.FIXED_TIME) {
            return Optional.of(PatientCancellationRefusal.QUEUE_BOOKING);
        }
        if (slot.isUntimed()) {
            return Optional.of(PatientCancellationRefusal.WALK_IN);
        }
        LocalDateTime scheduledAt = LocalDateTime.of(slot.getSession().getSessionDate(), slot.getStartTime());
        if (scheduledAt.isBefore(now.get().plusHours(CUTOFF_HOURS))) {
            return Optional.of(PatientCancellationRefusal.CUTOFF_PASSED);
        }
        return Optional.empty();
    }
}
