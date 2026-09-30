package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingCancelledEvent;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.exception.SlotNotFoundException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingProtectionService;
import com.cms.booking.service.FeeResolutionService;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.QueuePositionService;
import com.cms.booking.service.SessionPartialCancellationService;
import com.cms.booking.service.StaffBookingService;
import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.notification.service.NotificationEventService;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.patient.record.service.PatientLinkingService;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.waitlist.service.WaitlistBumpListener;
import com.cms.waitlist.service.WaitlistMatchingService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 063-front-desk-walk-in (research.md Decision 3, tasks.md T005): an untimed walk-in slot inside a
 * Fixed-Time session is never offered to the waitlist, never booked directly by slot id, never
 * crashes a cutoff-based partial cancellation, and gets a real position in its session's walk-in
 * line.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UntimedSlotGuardsTest {

    @Mock SlotRepository slotRepository;
    @Mock SessionRepository sessionRepository;
    @Mock BookingRepository bookingRepository;
    @Mock WaitlistMatchingService waitlistMatchingService;
    @Mock RoleAssignmentRepository roleAssignmentRepository;
    @Mock FeeResolutionService feeResolutionService;
    @Mock AppointmentTypeRepository appointmentTypeRepository;
    @Mock PatientRepository patientRepository;
    @Mock PatientLinkingService patientLinkingService;
    @Mock DoctorProfileRepository doctorProfileRepository;
    @Mock BookingProtectionService bookingProtectionService;
    @Mock ClinicRepository clinicRepository;
    @Mock IndianMobileNumberValidator mobileNumberValidator;
    @Mock NotificationEventService notificationEventService;
    @Mock SessionAvailabilityService sessionAvailabilityService;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private Session session;

    @BeforeEach
    void fixedTimeSessionTomorrow() {
        Clinic clinic = new Clinic("Clinic", "1 Main St", null, null);
        ReflectionTestUtils.setField(clinic, "id", clinicId);
        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getClinic()).thenReturn(clinic);
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        when(session.getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    private Slot saved(Slot slot, SlotStatus status) {
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        slot.setStatus(status);
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
        return slot;
    }

    private Slot walkIn(int token, SlotStatus status) {
        return saved(new Slot(session, token), status);
    }

    private Slot timed(LocalTime start, SlotStatus status) {
        return saved(new Slot(session, start, start.plusMinutes(15)), status);
    }

    @Test
    void theWaitlistIsNeverOfferedAnUntimedWalkInSlot() {
        Slot walkIn = walkIn(1, SlotStatus.OPEN);

        new WaitlistBumpListener(slotRepository, waitlistMatchingService)
                .onBookingCancelled(BookingCancelledEvent.of(UUID.randomUUID(), walkIn.getId()));

        verify(waitlistMatchingService, never()).matchAndOffer(any(), any());
    }

    @Test
    void aTimedFixedTimeSlotIsStillOfferedToTheWaitlist() {
        Slot timed = timed(LocalTime.of(10, 0), SlotStatus.OPEN);

        new WaitlistBumpListener(slotRepository, waitlistMatchingService)
                .onBookingCancelled(BookingCancelledEvent.of(UUID.randomUUID(), timed.getId()));

        verify(waitlistMatchingService).matchAndOffer(session, timed);
    }

    @Test
    void staffCannotBookAnUntimedSlotDirectly() {
        Slot walkIn = walkIn(1, SlotStatus.OPEN);
        StaffBookingService service = new StaffBookingService(
                slotRepository, roleAssignmentRepository, feeResolutionService, appointmentTypeRepository,
                patientRepository, bookingRepository, mobileNumberValidator, sessionAvailabilityService);

        assertThatThrownBy(() -> service.bookSlot(
                        callerId, clinicId, walkIn.getId(),
                        new StaffBookingService.BookSlotInput(null, "X", null, UUID.randomUUID())))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void aPatientCannotBookAnUntimedSlotDirectly() {
        Slot walkIn = walkIn(1, SlotStatus.OPEN);
        PatientBookingService service = new PatientBookingService(
                slotRepository, sessionRepository, feeResolutionService, appointmentTypeRepository,
                patientLinkingService, bookingRepository, doctorProfileRepository, bookingProtectionService,
                clinicRepository, sessionAvailabilityService, mock(org.springframework.transaction.PlatformTransactionManager.class));

        assertThatThrownBy(() -> service.bookSlot(
                        callerId, clinicId, walkIn.getId(), new PatientBookingService.BookSlotInput("X", UUID.randomUUID())))
                .isInstanceOf(SlotNotFoundException.class);
    }

    @Test
    void aCutoffPartialCancellationSkipsUntimedWalkInsWithoutFailing() {
        Slot walkIn = walkIn(1, SlotStatus.BOOKED);
        Slot afterCutoff = timed(LocalTime.of(12, 0), SlotStatus.BOOKED);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(walkIn, afterCutoff));
        Booking timedBooking = mock(Booking.class, Mockito.RETURNS_DEEP_STUBS);
        when(timedBooking.getId()).thenReturn(UUID.randomUUID());
        when(timedBooking.getPatient().getPatientAccount()).thenReturn(null);
        when(bookingRepository.findBySlot_IdAndStatus(afterCutoff.getId(), BookingStatus.ACTIVE))
                .thenReturn(Optional.of(timedBooking));
        when(bookingRepository.cancelIfActive(timedBooking.getId())).thenReturn(1);

        int cancelled = new SessionPartialCancellationService(
                        slotRepository, bookingRepository, notificationEventService, sessionAvailabilityService)
                .cancelFromCutoff(session, LocalTime.of(11, 0), null, callerId);

        assertThat(cancelled).isEqualTo(1);
        assertThat(walkIn.getStatus()).isEqualTo(SlotStatus.BOOKED);
    }

    @Test
    void aFixedTimeWalkInsPositionCountsOnlyWaitingWalkInsAheadOfIt() {
        Slot first = walkIn(1, SlotStatus.BOOKED);
        Slot seen = walkIn(2, SlotStatus.COMPLETED);
        Slot mine = walkIn(3, SlotStatus.BOOKED);
        Slot timedBooked = timed(LocalTime.of(9, 0), SlotStatus.BOOKED);
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of(timedBooked, first, seen, mine));
        Booking booking = mock(Booking.class);
        when(booking.getSlot()).thenReturn(mine);

        QueuePositionService.QueuePosition position = new QueuePositionService(slotRepository).positionOf(booking);

        assertThat(position.applicable()).isTrue();
        assertThat(position.position()).isEqualTo(2);
    }

    @Test
    void aTimedFixedTimeBookingHasNoQueuePosition() {
        Slot timedBooked = timed(LocalTime.of(9, 0), SlotStatus.BOOKED);
        Booking booking = mock(Booking.class);
        when(booking.getSlot()).thenReturn(timedBooked);

        assertThat(new QueuePositionService(slotRepository).positionOf(booking).applicable()).isFalse();
    }
}
