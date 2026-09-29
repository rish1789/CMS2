package com.cms.identity.admin.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideChangeLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.service.PasswordPolicyValidator;
import com.cms.identity.account.service.TemporaryPasswordGenerator;
import com.cms.identity.admin.domain.ClinicRejectedEvent;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import com.cms.identity.admin.service.ClinicVerificationService;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.inbox.repository.InboxItemRepository;
import com.cms.patient.record.repository.PatientRepository;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import com.cms.scheduling.repository.ScheduleRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 062-rejected-clinic-gating (research.md Decision 3, tasks.md T002): rejecting a clinic publishes
 * {@link ClinicRejectedEvent} exactly once per genuine not-rejected -> rejected transition, so the
 * booking and waitlist modules can react after commit.
 */
@ExtendWith(MockitoExtension.class)
class ClinicRejectedEventPublicationTest {

    @Mock ClinicRepository clinicRepository;
    @Mock RoleAssignmentRepository roleAssignmentRepository;
    @Mock InboxItemRepository inboxItemRepository;
    @Mock WaitlistEntryRepository waitlistEntryRepository;
    @Mock PatientRepository patientRepository;
    @Mock ScheduleRepository scheduleRepository;
    @Mock SessionRepository sessionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock AccountRepository accountRepository;
    @Mock TemporaryPasswordGenerator temporaryPasswordGenerator;
    @Mock PasswordPolicyValidator passwordPolicyValidator;
    @Mock PasswordEncoder passwordEncoder;
    @Mock ClinicBookingLimitOverrideRepository overrideRepository;
    @Mock ClinicBookingLimitOverrideChangeLogRepository overrideChangeLogRepository;
    @Mock BookingAttemptLogRepository bookingAttemptLogRepository;
    @Mock SuspiciousActivityFlagRepository suspiciousActivityFlagRepository;

    private ClinicVerificationService service;

    @BeforeEach
    void setUp() {
        service = new ClinicVerificationService(
                clinicRepository,
                roleAssignmentRepository,
                inboxItemRepository,
                waitlistEntryRepository,
                patientRepository,
                scheduleRepository,
                sessionRepository,
                eventPublisher,
                accountRepository,
                temporaryPasswordGenerator,
                passwordPolicyValidator,
                passwordEncoder,
                overrideRepository,
                overrideChangeLogRepository,
                bookingAttemptLogRepository,
                suspiciousActivityFlagRepository);
    }

    private Clinic pendingClinic() {
        Clinic clinic = new Clinic("Pending Clinic", "1 Main St", "pending@example.com", "9999900000");
        UUID id = UUID.randomUUID();
        ReflectionTestUtils.setField(clinic, "id", id);
        when(clinicRepository.findById(id)).thenReturn(Optional.of(clinic));
        return clinic;
    }

    @Test
    void rejectingAPendingClinicPublishesOneEventWithItsId() {
        Clinic clinic = pendingClinic();

        service.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, "super-admin");

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(ClinicRejectedEvent.class);
        assertThat(((ClinicRejectedEvent) captor.getValue()).clinicId()).isEqualTo(clinic.getId());
    }

    @Test
    void rejectingAnAlreadyRejectedClinicPublishesNothing() {
        Clinic clinic = pendingClinic();
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");

        service.reject(clinic.getId(), "DUPLICATE_REGISTRATION", null, "super-admin");

        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void bulkRejectingTwoPendingClinicsPublishesTwoEvents() {
        Clinic first = pendingClinic();
        Clinic second = pendingClinic();

        service.rejectBulk(List.of(first.getId(), second.getId()), "DUPLICATE_REGISTRATION", null, "super-admin");

        verify(eventPublisher, times(2)).publishEvent(any(ClinicRejectedEvent.class));
    }
}
