package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.exception.SlotDateInThePastException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingProtectionService;
import com.cms.booking.service.FeeResolutionService;
import com.cms.booking.service.FrontDeskWalkInService;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.booking.service.QueuePositionService;
import com.cms.booking.service.StaffBookingService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.inbox.service.InboxItemService;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.patient.record.service.PatientLinkingService;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.scheduling.service.QueueSlotService;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.scheduling.service.SessionAvailabilityService.Verdict;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 065-phase1-stabilization (tasks.md T015/T026, research.md R4): all five booking paths refuse what
 * {@link SessionAvailabilityService} rejects, before any write. A past or elapsed timed slot keeps
 * the existing {@link SlotDateInThePastException}; a cancelled session/range, or a queue/walk-in
 * request for a past session, is {@link SessionNotAcceptingBookingsException}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingPathsAvailabilityTest {

    @Mock SlotRepository slotRepository;
    @Mock SessionRepository sessionRepository;
    @Mock FeeResolutionService feeResolutionService;
    @Mock AppointmentTypeRepository appointmentTypeRepository;
    @Mock PatientLinkingService patientLinkingService;
    @Mock BookingRepository bookingRepository;
    @Mock DoctorProfileRepository doctorProfileRepository;
    @Mock BookingProtectionService bookingProtectionService;
    @Mock QueueSlotService queueSlotService;
    @Mock RoleAssignmentRepository roleAssignmentRepository;
    @Mock PatientRepository patientRepository;
    @Mock IndianMobileNumberValidator mobileNumberValidator;
    @Mock ClinicRepository clinicRepository;
    @Mock QueuePositionService queuePositionService;
    @Mock InboxItemService inboxItemService;
    @Mock SessionAvailabilityService availability;

    private final UUID callerId = UUID.randomUUID();
    private UUID clinicId;
    private Session session;
    private Slot slot;

    @BeforeEach
    void acceptingClinicWithASessionAndSlot() {
        Clinic clinic = new Clinic("Clinic", "1 Main St", "clinic@example.com", "9999900000");
        clinicId = UUID.randomUUID();
        ReflectionTestUtils.setField(clinic, "id", clinicId);
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));

        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getClinic()).thenReturn(clinic);
        when(session.getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
        slot = new Slot(session, LocalTime.of(10, 0), LocalTime.of(10, 15));
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    private void verdict(Verdict verdict) {
        when(availability.evaluate(eq(session), any())).thenReturn(verdict);
    }

    private void assertNothingWritten() {
        verifyNoInteractions(bookingRepository, feeResolutionService, patientLinkingService, patientRepository, queueSlotService);
    }

    @Nested
    class PatientFixedTime {
        private PatientBookingService service() {
            when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
            return new PatientBookingService(
                    slotRepository, sessionRepository, feeResolutionService, appointmentTypeRepository,
                    patientLinkingService, bookingRepository, doctorProfileRepository, bookingProtectionService,
                    clinicRepository, availability, mock(org.springframework.transaction.PlatformTransactionManager.class));
        }

        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"PAST_DATE", "ELAPSED"})
        void aPastOrElapsedSlotIsSlotDateInThePast(Verdict v) {
            verdict(v);
            assertThatThrownBy(() -> service().bookSlot(
                            callerId, clinicId, slot.getId(), new PatientBookingService.BookSlotInput("Asha", UUID.randomUUID())))
                    .isInstanceOf(SlotDateInThePastException.class);
            assertNothingWritten();
        }

        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = "CANCELLED")
        void aCancelledSessionIsNotAcceptingBookings(Verdict v) {
            verdict(v);
            assertThatThrownBy(() -> service().bookSlot(
                            callerId, clinicId, slot.getId(), new PatientBookingService.BookSlotInput("Asha", UUID.randomUUID())))
                    .isInstanceOf(SessionNotAcceptingBookingsException.class);
            assertNothingWritten();
        }
    }

    @Nested
    class StaffFixedTime {
        private StaffBookingService service() {
            when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
            return new StaffBookingService(
                    slotRepository, roleAssignmentRepository, feeResolutionService, appointmentTypeRepository,
                    patientRepository, bookingRepository, mobileNumberValidator, availability);
        }

        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"PAST_DATE", "ELAPSED"})
        void aPastOrElapsedSlotIsSlotDateInThePast(Verdict v) {
            verdict(v);
            assertThatThrownBy(() -> service().bookSlot(
                            callerId, clinicId, slot.getId(),
                            new StaffBookingService.BookSlotInput(null, "Walk In", "9876543210", UUID.randomUUID())))
                    .isInstanceOf(SlotDateInThePastException.class);
            assertNothingWritten();
        }

        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = "CANCELLED")
        void aCancelledSessionIsNotAcceptingBookings(Verdict v) {
            verdict(v);
            assertThatThrownBy(() -> service().bookSlot(
                            callerId, clinicId, slot.getId(),
                            new StaffBookingService.BookSlotInput(null, "Walk In", "9876543210", UUID.randomUUID())))
                    .isInstanceOf(SessionNotAcceptingBookingsException.class);
            assertNothingWritten();
        }
    }

    @Nested
    class PatientQueue {
        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"PAST_DATE", "CANCELLED"})
        void isNotAcceptingBookings(Verdict v) {
            when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
            verdict(v);
            PatientQueueBookingService service = new PatientQueueBookingService(
                    sessionRepository, queueSlotService, feeResolutionService, appointmentTypeRepository,
                    patientLinkingService, bookingRepository, bookingProtectionService, clinicRepository, availability);

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, session.getId(),
                            new PatientQueueBookingService.BookSlotInput("Asha", UUID.randomUUID())))
                    .isInstanceOf(SessionNotAcceptingBookingsException.class);
            assertNothingWritten();
        }
    }

    @Nested
    class StaffQueue {
        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"PAST_DATE", "CANCELLED"})
        void isNotAcceptingBookings(Verdict v) {
            when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
            verdict(v);
            StaffQueueBookingService service = new StaffQueueBookingService(
                    sessionRepository, queueSlotService, roleAssignmentRepository, feeResolutionService,
                    appointmentTypeRepository, patientRepository, bookingRepository, mobileNumberValidator, availability);

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, session.getId(),
                            new StaffQueueBookingService.BookSlotInput(null, "Walk In", "9876543210", UUID.randomUUID())))
                    .isInstanceOf(SessionNotAcceptingBookingsException.class);
            assertNothingWritten();
        }
    }

    @Nested
    class FrontDeskWalkIn {
        @ParameterizedTest
        @EnumSource(value = Verdict.class, names = {"PAST_DATE", "CANCELLED"})
        void isNotAcceptingWalkInsInEitherMode(Verdict v) {
            verdict(v);
            FrontDeskWalkInService service = new FrontDeskWalkInService(
                    sessionRepository, roleAssignmentRepository, feeResolutionService, appointmentTypeRepository,
                    patientRepository, bookingRepository, mobileNumberValidator, queueSlotService, queuePositionService,
                    inboxItemService, availability);
            FrontDeskWalkInService.RegisterInput input = new FrontDeskWalkInService.RegisterInput(
                    session.getId(), null, "Asha Rao", "9876543210", null, UUID.randomUUID(), "PAIN", null, false);

            for (ScheduleMode mode : ScheduleMode.values()) {
                when(session.getMode()).thenReturn(mode);
                assertThatThrownBy(() -> service.register(callerId, clinicId, input))
                        .isInstanceOf(SessionNotAcceptingBookingsException.class);
            }
            assertNothingWritten();
            verifyNoInteractions(inboxItemService);
        }
    }
}
