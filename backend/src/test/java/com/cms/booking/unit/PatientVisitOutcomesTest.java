package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.PatientCancellationRefusal;
import com.cms.booking.domain.VisitOutcome;
import com.cms.booking.service.PatientVisitOutcomes;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 069-patient-visit-outcomes (spec "Decision table"): the patient's own visit outcome and
 * cancellation eligibility, derived from booking state, own slot status and the server's
 * operational date. Pure unit test with a fixed clock - no Spring, no Docker.
 */
class PatientVisitOutcomesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(TODAY, LocalTime.of(12, 0));

    private final PatientVisitOutcomes outcomes = new PatientVisitOutcomes(() -> NOW);

    private static Booking booking(BookingStatus bookingStatus, SlotStatus slotStatus, LocalDate date, LocalTime start) {
        return booking(bookingStatus, slotStatus, date, start, ScheduleMode.FIXED_TIME, false);
    }

    private static Booking booking(
            BookingStatus bookingStatus, SlotStatus slotStatus, LocalDate date, LocalTime start, ScheduleMode mode, boolean untimed) {
        Session session = mock(Session.class);
        lenient().when(session.getSessionDate()).thenReturn(date);
        lenient().when(session.getMode()).thenReturn(mode);
        Slot slot = mock(Slot.class);
        lenient().when(slot.getSession()).thenReturn(session);
        lenient().when(slot.getStatus()).thenReturn(slotStatus);
        lenient().when(slot.getStartTime()).thenReturn(start);
        lenient().when(slot.isUntimed()).thenReturn(untimed);
        Booking booking = mock(Booking.class);
        lenient().when(booking.getStatus()).thenReturn(bookingStatus);
        lenient().when(booking.getSlot()).thenReturn(slot);
        return booking;
    }

    // --- visit outcome -------------------------------------------------------------------------

    @Test
    void aCancelledBookingIsCancelledWhateverItsSlot() {
        assertThat(outcomes.outcome(booking(BookingStatus.CANCELLED, SlotStatus.OPEN, TODAY.plusDays(3), LocalTime.NOON)))
                .isEqualTo(VisitOutcome.CANCELLED);
    }

    @Test
    void ownCompletedSlotIsCompleted() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.COMPLETED, TODAY, LocalTime.of(9, 0))))
                .isEqualTo(VisitOutcome.COMPLETED);
    }

    @Test
    void ownNoShowIsNoShowNeverCompleted() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.NO_SHOW, TODAY, LocalTime.of(11, 0))))
                .isEqualTo(VisitOutcome.NO_SHOW);
    }

    @Test
    void appearedTodayIsCheckedIn() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.APPEARED, TODAY, LocalTime.of(11, 0))))
                .isEqualTo(VisitOutcome.CHECKED_IN);
    }

    /** The delayed rule: elapsed time today is never treated as completion or a miss. */
    @Test
    void bookedTodayAfterItsStartTimeIsStillScheduled() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY, LocalTime.of(10, 0))))
                .isEqualTo(VisitOutcome.SCHEDULED);
    }

    @Test
    void bookedInTheFutureIsScheduled() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(5), LocalTime.of(10, 0))))
                .isEqualTo(VisitOutcome.SCHEDULED);
    }

    @Test
    void unresolvedOnAPastDayIsNotRecorded() {
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.minusDays(1), LocalTime.of(10, 0))))
                .isEqualTo(VisitOutcome.NOT_RECORDED);
        assertThat(outcomes.outcome(booking(BookingStatus.ACTIVE, SlotStatus.APPEARED, TODAY.minusDays(1), LocalTime.of(10, 0))))
                .isEqualTo(VisitOutcome.NOT_RECORDED);
    }

    @Test
    void aLegacyOpenQueueTokenOnAnActiveBookingIsTreatedAsBooked() {
        Booking queue = booking(BookingStatus.ACTIVE, SlotStatus.OPEN, TODAY, null, ScheduleMode.QUEUE, false);
        assertThat(outcomes.outcome(queue)).isEqualTo(VisitOutcome.SCHEDULED);
    }

    // --- cancellation eligibility ----------------------------------------------------------------

    @Test
    void aFutureFixedTimeBookingOutsideTheCutoffIsCancellable() {
        var eligibility = outcomes.eligibility(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(2), LocalTime.of(10, 0)));
        assertThat(eligibility.allowed()).isTrue();
        assertThat(eligibility.reason()).isNull();
    }

    @Test
    void exactlyTwoHoursAwayIsStillCancellableButOneMinuteLessIsNot() {
        assertThat(outcomes.eligibility(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY, LocalTime.of(14, 0))).allowed())
                .isTrue();
        assertThat(outcomes.eligibility(booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY, LocalTime.of(13, 59))).reason())
                .isEqualTo(PatientCancellationRefusal.CUTOFF_PASSED);
    }

    @Test
    void eachRefusalReason() {
        assertThat(outcomes.eligibility(booking(BookingStatus.CANCELLED, SlotStatus.OPEN, TODAY.plusDays(2), LocalTime.NOON)).reason())
                .isEqualTo(PatientCancellationRefusal.ALREADY_CANCELLED);
        assertThat(outcomes.eligibility(booking(BookingStatus.ACTIVE, SlotStatus.COMPLETED, TODAY.plusDays(2), LocalTime.NOON)).reason())
                .isEqualTo(PatientCancellationRefusal.VISIT_RESOLVED);
        assertThat(outcomes.eligibility(
                                booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(2), null, ScheduleMode.QUEUE, false))
                        .reason())
                .isEqualTo(PatientCancellationRefusal.QUEUE_BOOKING);
        assertThat(outcomes.eligibility(
                                booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(2), null, ScheduleMode.FIXED_TIME, true))
                        .reason())
                .isEqualTo(PatientCancellationRefusal.WALK_IN);
    }

    /** Display order: the resolved outcome is the more truthful explanation than the elapsed cutoff. */
    @Test
    void aPastNoShowReportsVisitResolvedNotCutoff() {
        assertThat(outcomes.eligibility(booking(BookingStatus.ACTIVE, SlotStatus.NO_SHOW, TODAY, LocalTime.of(11, 0))).reason())
                .isEqualTo(PatientCancellationRefusal.VISIT_RESOLVED);
    }

    /** The endpoint's own order is unchanged: queue, walk-in, cutoff - slot state is left to the service. */
    @Test
    void requestRefusalKeepsTheEndpointsExistingOrder() {
        assertThat(outcomes.requestRefusal(
                        booking(BookingStatus.ACTIVE, SlotStatus.NO_SHOW, TODAY, LocalTime.of(11, 0)).getSlot()))
                .contains(PatientCancellationRefusal.CUTOFF_PASSED);
        assertThat(outcomes.requestRefusal(
                        booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(2), null, ScheduleMode.QUEUE, false).getSlot()))
                .contains(PatientCancellationRefusal.QUEUE_BOOKING);
        assertThat(outcomes.requestRefusal(
                        booking(BookingStatus.ACTIVE, SlotStatus.BOOKED, TODAY.plusDays(2), LocalTime.NOON).getSlot()))
                .isEmpty();
    }
}
