package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.AppointmentType;
import com.cms.booking.domain.Booking;
import com.cms.booking.domain.BookingSource;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.VisitReason;
import com.cms.booking.exception.ClinicNotAcceptingAppointmentsException;
import com.cms.booking.exception.DuplicateWalkInException;
import com.cms.booking.exception.ForbiddenException;
import com.cms.booking.exception.InvalidEmailException;
import com.cms.booking.exception.InvalidMobileNumberException;
import com.cms.booking.exception.PatientNotFoundException;
import com.cms.booking.exception.PatientRequiredException;
import com.cms.booking.exception.VisitReasonDetailRequiredException;
import com.cms.booking.exception.VisitReasonRequiredException;
import com.cms.booking.repository.AppointmentTypeRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.FeeResolutionService;
import com.cms.booking.service.FrontDeskWalkInService;
import com.cms.booking.service.FrontDeskWalkInService.RegisterInput;
import com.cms.booking.service.FrontDeskWalkInService.Registration;
import com.cms.booking.service.QueuePositionService;
import com.cms.common.IndianMobileNumberValidator;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.inbox.service.InboxItemService;
import com.cms.patient.record.domain.Patient;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.service.QueueSlotService;
import com.cms.scheduling.service.SessionAvailabilityService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 063-front-desk-walk-in (FR-002-FR-011, FR-017, FR-018, research.md Decision 4, tasks.md T014):
 * one front-desk registration for both session modes. A Fixed-Time walk-in joins the session's
 * untimed walk-in line and never touches a timed slot; a Queue walk-in takes the next token. Every
 * walk-in carries a visit reason, is marked WALK_IN and raises the existing inbox walk-in item.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FrontDeskWalkInServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock RoleAssignmentRepository roleAssignmentRepository;
    @Mock FeeResolutionService feeResolutionService;
    @Mock AppointmentTypeRepository appointmentTypeRepository;
    @Mock PatientRepository patientRepository;
    @Mock BookingRepository bookingRepository;
    @Mock IndianMobileNumberValidator mobileNumberValidator;
    @Mock QueueSlotService queueSlotService;
    @Mock QueuePositionService queuePositionService;
    @Mock InboxItemService inboxItemService;
    @Mock SessionAvailabilityService sessionAvailabilityService;

    private final UUID clinicId = UUID.randomUUID();
    private final UUID callerId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID appointmentTypeId = UUID.randomUUID();
    private Clinic clinic;
    private Session session;
    private AppointmentType appointmentType;

    private FrontDeskWalkInService service() {
        return new FrontDeskWalkInService(
                sessionRepository, roleAssignmentRepository, feeResolutionService, appointmentTypeRepository,
                patientRepository, bookingRepository, mobileNumberValidator, queueSlotService, queuePositionService,
                inboxItemService, sessionAvailabilityService);
    }

    @BeforeEach
    void setUp() {
        clinic = new Clinic("Clinic", "1 Main St", null, null);
        ReflectionTestUtils.setField(clinic, "id", clinicId);
        session = mock(Session.class, Mockito.RETURNS_DEEP_STUBS);
        when(session.getId()).thenReturn(sessionId);
        when(session.getClinic()).thenReturn(clinic);
        when(session.getSessionDate()).thenReturn(LocalDate.now());
        when(session.getDoctorProfile().getId()).thenReturn(UUID.randomUUID());
        when(session.getDoctorProfile().getAccount().getName()).thenReturn("Dr. Rao");
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                        callerId, clinicId, RoleAssignment.Role.Operations))
                .thenReturn(true);
        appointmentType = mock(AppointmentType.class);
        when(appointmentType.getId()).thenReturn(appointmentTypeId);
        when(appointmentTypeRepository.findById(appointmentTypeId)).thenReturn(Optional.of(appointmentType));
        // 068: the walk-in resolves at its own clinic.
        when(feeResolutionService.resolve(eq(clinicId), any(), eq(appointmentTypeId))).thenReturn(new BigDecimal("450.00"));
        when(mobileNumberValidator.isValid(any())).thenReturn(true);
        when(patientRepository.save(any(Patient.class))).thenAnswer(inv -> inv.getArgument(0));
        when(bookingRepository.saveAndFlush(any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        when(queuePositionService.positionOf(any())).thenReturn(new QueuePositionService.QueuePosition(true, 1));
        // 065-phase1-stabilization: an open session - the refusals are covered in BookingPathsAvailabilityTest.
        when(sessionAvailabilityService.evaluate(any(), any())).thenReturn(SessionAvailabilityService.Verdict.ACCEPTING);
    }

    private void fixedTime() {
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        Slot walkInSlot = new Slot(session, 1);
        walkInSlot.setStatus(SlotStatus.BOOKED); // QueueSlotService mints tokens waiting (064; see QueueSlotServiceWalkInTest)
        when(queueSlotService.issueNextWalkInSlot(sessionId)).thenReturn(walkInSlot);
    }

    private void queue() {
        when(session.getMode()).thenReturn(ScheduleMode.QUEUE);
        when(queueSlotService.issueNextSlot(sessionId)).thenReturn(new Slot(session, 7));
    }

    private RegisterInput newPatient(String reason, String detail) {
        return new RegisterInput(
                sessionId, null, "Asha Rao", "9876543210", "asha@example.com", appointmentTypeId, reason, detail, false);
    }

    private Booking savedBooking() {
        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void aFixedTimeWalkInJoinsTheUntimedWalkInLineWithItsReasonAndLockedFee() {
        fixedTime();

        Registration result = service().register(callerId, clinicId, newPatient("PAIN", null));

        Booking booking = savedBooking();
        assertThat(booking.getSource()).isEqualTo(BookingSource.WALK_IN);
        assertThat(booking.getVisitReason()).isEqualTo(VisitReason.PAIN);
        assertThat(booking.getLockedFee()).isEqualByComparingTo("450.00");
        assertThat(booking.getSlot().isUntimed()).isTrue();
        assertThat(booking.getSlot().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(booking.getPatient().getEmail()).isEqualTo("asha@example.com");
        assertThat(result.booking()).isSameAs(booking);
        assertThat(result.walkInPosition()).isEqualTo(1);
        verify(queueSlotService, never()).issueNextSlot(any());
        verify(inboxItemService).createWalkInItem(clinic, booking);
    }

    @Test
    void aQueueWalkInTakesTheNextTokenAndIsMarkedAsAWalkIn() {
        queue();

        Registration result = service().register(callerId, clinicId, newPatient("FEVER_COLD_COUGH", null));

        Booking booking = savedBooking();
        assertThat(booking.getSource()).isEqualTo(BookingSource.WALK_IN);
        assertThat(booking.getSlot().getTokenNumber()).isEqualTo(7);
        assertThat(result.walkInPosition()).isNull();
        verify(queueSlotService, never()).issueNextWalkInSlot(any());
        verify(inboxItemService).createWalkInItem(clinic, booking);
    }

    @Test
    void aVisitReasonIsRequired() {
        fixedTime();

        assertThatThrownBy(() -> service().register(callerId, clinicId, newPatient(null, null)))
                .isInstanceOf(VisitReasonRequiredException.class);
        assertThatThrownBy(() -> service().register(callerId, clinicId, newPatient("HEADACHE", null)))
                .isInstanceOf(VisitReasonRequiredException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void otherNeedsDetailOfAtMost200Characters() {
        fixedTime();

        assertThatThrownBy(() -> service().register(callerId, clinicId, newPatient("OTHER", "  ")))
                .isInstanceOf(VisitReasonDetailRequiredException.class);
        assertThatThrownBy(() -> service().register(callerId, clinicId, newPatient("OTHER", "x".repeat(201))))
                .isInstanceOf(VisitReasonDetailRequiredException.class);

        service().register(callerId, clinicId, newPatient("OTHER", "Dizziness since morning"));
        assertThat(savedBooking().getVisitReasonDetail()).isEqualTo("Dizziness since morning");
    }

    @Test
    void aPatientIsRequired() {
        fixedTime();

        assertThatThrownBy(() -> service().register(callerId, clinicId, new RegisterInput(
                        sessionId, null, "  ", null, null, appointmentTypeId, "PAIN", null, false)))
                .isInstanceOf(PatientRequiredException.class);
    }

    @Test
    void aMalformedEmailOrPhoneIsRejected() {
        fixedTime();

        assertThatThrownBy(() -> service().register(callerId, clinicId, new RegisterInput(
                        sessionId, null, "Asha", null, "not-an-email", appointmentTypeId, "PAIN", null, false)))
                .isInstanceOf(InvalidEmailException.class);
        when(mobileNumberValidator.isValid("12345")).thenReturn(false);
        assertThatThrownBy(() -> service().register(callerId, clinicId, new RegisterInput(
                        sessionId, null, "Asha", "12345", null, appointmentTypeId, "PAIN", null, false)))
                .isInstanceOf(InvalidMobileNumberException.class);
    }

    @Test
    void aPatientAlreadyInThisSessionNeedsConfirmation() {
        fixedTime();
        UUID patientId = UUID.randomUUID();
        Patient existing = new Patient(clinic, null, "Asha", null);
        ReflectionTestUtils.setField(existing, "id", patientId);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(existing));
        when(bookingRepository.existsBySlot_Session_IdAndPatient_IdAndStatus(sessionId, patientId, BookingStatus.ACTIVE))
                .thenReturn(true);

        assertThatThrownBy(() -> service().register(callerId, clinicId, new RegisterInput(
                        sessionId, patientId, null, null, null, appointmentTypeId, "PAIN", null, false)))
                .isInstanceOf(DuplicateWalkInException.class);

        service().register(callerId, clinicId, new RegisterInput(
                sessionId, patientId, null, null, null, appointmentTypeId, "PAIN", null, true));
        assertThat(savedBooking().getPatient()).isSameAs(existing);
    }

    @Test
    void aRejectedClinicIsRefusedBeforeAnyWrite() {
        fixedTime();
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");

        assertThatThrownBy(() -> service().register(callerId, clinicId, newPatient("PAIN", null)))
                .isInstanceOf(ClinicNotAcceptingAppointmentsException.class);
        verifyNoInteractions(bookingRepository, patientRepository, queueSlotService, feeResolutionService);
    }

    @Test
    void onlyOperationsOrClinicAdminMayRegister() {
        fixedTime();
        UUID doctorCaller = UUID.randomUUID();

        assertThatThrownBy(() -> service().register(doctorCaller, clinicId, newPatient("PAIN", null)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aPatientFromAnotherClinicIsNotFound() {
        fixedTime();
        UUID patientId = UUID.randomUUID();
        Clinic other = new Clinic("Other", "2 Main St", null, null);
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        Patient foreign = new Patient(other, null, "Someone", null);
        when(patientRepository.findById(patientId)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service().register(callerId, clinicId, new RegisterInput(
                        sessionId, patientId, null, null, null, appointmentTypeId, "PAIN", null, false)))
                .isInstanceOf(PatientNotFoundException.class);
    }
}
