package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.BookingProtectionService;
import com.cms.booking.service.FeeResolutionService;
import com.cms.booking.service.PatientBookingService;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.scheduling.service.QueueSlotService;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.booking.service.StaffBookingService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.identity.doctor.DoctorProfileRepository;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.patient.record.service.PatientLinkingService;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 062-rejected-clinic-gating (FR-001/FR-003, tasks.md T006): every service that creates a Booking
 * refuses a rejected clinic before any write. The two patient paths refuse before 060's
 * rate-limit gate (research.md Decision 1, "Ordering") so a refused attempt is never recorded as
 * booking activity; the three staff paths refuse right after their existing role check.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RejectedClinicBookingRefusalTest {

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
    @Mock SessionAvailabilityService sessionAvailabilityService;

    private final UUID callerId = UUID.randomUUID();
    private Clinic clinic;
    private UUID clinicId;
    private Session session;
    private Slot slot;

    @BeforeEach
    void rejectedClinicWithASessionAndSlot() {
        clinic = new Clinic("Rejected Clinic", "1 Main St", "rejected@example.com", "9999900000");
        clinicId = UUID.randomUUID();
        ReflectionTestUtils.setField(clinic, "id", clinicId);
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));

        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getClinic()).thenReturn(clinic);
        when(session.getSessionDate()).thenReturn(LocalDate.now().plusDays(1));
        slot = new Slot(session, LocalTime.of(10, 0), LocalTime.of(10, 15));
        ReflectionTestUtils.setField(slot, "id", UUID.randomUUID());
        when(slotRepository.findById(slot.getId())).thenReturn(Optional.of(slot));
        when(sessionRepository.findById(session.getId())).thenReturn(Optional.of(session));

        // A ClinicAdmin caller passes every staff path's existing role check - the rejection
        // refusal is what must stop them.
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    private void assertNothingWritten() {
        verifyNoInteractions(
                bookingRepository, feeResolutionService, patientLinkingService, patientRepository, sessionAvailabilityService);
    }

    @Nested
    class PatientFixedTime {
        @Test
        void refusesBeforeTheRateLimitGateAndWritesNothing() {
            when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
            PatientBookingService service = new PatientBookingService(
                    slotRepository, sessionRepository, feeResolutionService, appointmentTypeRepository,
                    patientLinkingService, bookingRepository, doctorProfileRepository, bookingProtectionService,
                    clinicRepository, sessionAvailabilityService, mock(org.springframework.transaction.PlatformTransactionManager.class));

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, slot.getId(), new PatientBookingService.BookSlotInput("Asha", UUID.randomUUID())))
                    .isInstanceOf(ClinicNotAcceptingAppointmentsException.class);
            assertNothingWritten();
            verifyNoInteractions(bookingProtectionService);
        }
    }

    @Nested
    class PatientQueue {
        @Test
        void refusesBeforeTheRateLimitGateAndWritesNothing() {
            when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
            PatientQueueBookingService service = new PatientQueueBookingService(
                    sessionRepository, queueSlotService, feeResolutionService, appointmentTypeRepository,
                    patientLinkingService, bookingRepository, bookingProtectionService, clinicRepository,
                    sessionAvailabilityService);

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, session.getId(),
                            new PatientQueueBookingService.BookSlotInput("Asha", UUID.randomUUID())))
                    .isInstanceOf(ClinicNotAcceptingAppointmentsException.class);
            assertNothingWritten();
            verifyNoInteractions(bookingProtectionService, queueSlotService);
        }
    }

    @Nested
    class StaffFixedTime {
        @Test
        void refusesAClinicAdminAndWritesNothing() {
            when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
            StaffBookingService service = new StaffBookingService(
                    slotRepository, roleAssignmentRepository, feeResolutionService, appointmentTypeRepository,
                    patientRepository, bookingRepository, mobileNumberValidator, sessionAvailabilityService);

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, slot.getId(),
                            new StaffBookingService.BookSlotInput(null, "Walk In", null, UUID.randomUUID())))
                    .isInstanceOf(ClinicNotAcceptingAppointmentsException.class);
            assertNothingWritten();
        }
    }

    @Nested
    class StaffQueue {
        @Test
        void refusesAClinicAdminAndWritesNothing() {
            when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
            StaffQueueBookingService service = new StaffQueueBookingService(
                    sessionRepository, queueSlotService, roleAssignmentRepository, feeResolutionService,
                    appointmentTypeRepository, patientRepository, bookingRepository, mobileNumberValidator,
                    sessionAvailabilityService);

            assertThatThrownBy(() -> service.bookSlot(
                            callerId, clinicId, session.getId(),
                            new StaffQueueBookingService.BookSlotInput(null, "Walk In", null, UUID.randomUUID())))
                    .isInstanceOf(ClinicNotAcceptingAppointmentsException.class);
            assertNothingWritten();
            verifyNoInteractions(queueSlotService);
        }
    }

    // 063-front-desk-walk-in: the walk-in path is now the front-desk registration, whose rejected
    // clinic refusal is covered in FrontDeskWalkInServiceTest.aRejectedClinicIsRefusedBeforeAnyWrite.
}
