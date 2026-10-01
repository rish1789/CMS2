package com.cms.identity.admin.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.ClinicAppointmentTypePriceRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideChangeLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.booking.repository.ClinicDoctorFeeRepository;
import com.cms.identity.account.repository.AccountRepository;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.account.service.PasswordPolicyValidator;
import com.cms.identity.account.service.TemporaryPasswordGenerator;
import com.cms.identity.admin.dto.BulkDeleteResponse;
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
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * real-bug-fix 2026-09-24: deleteGuarded predated 060-booking-abuse-prevention and never learned
 * about its four clinic-referencing tables, so deleting a rejected clinic with a booking-limit
 * override hit a foreign-key violation at commit - which rolled back the whole bulk batch.
 * Override + change log are clinic configuration (cleared, like role assignments); booking
 * attempts and suspicious-activity flags are real patient activity (block, like patient records).
 */
@ExtendWith(MockitoExtension.class)
class RejectedClinicDeletionGuardTest {

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
    private Clinic clinic;
    private UUID clinicId;

    @Mock
    private ClinicDoctorFeeRepository clinicDoctorFeeRepository;

    @Mock
    private ClinicAppointmentTypePriceRepository clinicAppointmentTypePriceRepository;

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
                suspiciousActivityFlagRepository,
                clinicDoctorFeeRepository,
                clinicAppointmentTypePriceRepository);
        clinic = new Clinic("Sunrise Test Clinic", "1 Main St", "sunrise@example.com", "9999900000");
        clinicId = UUID.randomUUID();
        ReflectionTestUtils.setField(clinic, "id", clinicId);
        clinic.reject(Clinic.RejectionReason.DUPLICATE_REGISTRATION, null, "super-admin");
        when(clinicRepository.findById(clinicId)).thenReturn(Optional.of(clinic));
    }

    @Test
    void aBookingLimitOverrideIsClearedAsConfigurationBeforeTheClinicIsDeleted() {
        BulkDeleteResponse result = service.deleteBulk(List.of(clinicId));

        assertThat(result.succeeded()).containsExactly(clinicId);
        assertThat(result.failed()).isEmpty();
        InOrder order = inOrder(overrideChangeLogRepository, overrideRepository, clinicRepository);
        order.verify(overrideChangeLogRepository).deleteByClinic_Id(clinicId);
        order.verify(overrideRepository).deleteByClinic_Id(clinicId);
        order.verify(clinicRepository).delete(clinic);
    }

    /** 068-per-clinic-fees: the clinic's own prices are configuration, cleared before the clinic. */
    @Test
    void theClinicsPricesAreClearedBeforeTheClinicIsDeleted() {
        BulkDeleteResponse result = service.deleteBulk(List.of(clinicId));

        assertThat(result.succeeded()).containsExactly(clinicId);
        InOrder order = inOrder(clinicAppointmentTypePriceRepository, clinicDoctorFeeRepository, clinicRepository);
        order.verify(clinicAppointmentTypePriceRepository).deleteByClinic_Id(clinicId);
        order.verify(clinicDoctorFeeRepository).deleteByClinic_Id(clinicId);
        order.verify(clinicRepository).delete(clinic);
    }

    @Test
    void bookingAttemptsBlockDeletionAsRealActivity() {
        when(bookingAttemptLogRepository.countByClinic_Id(clinicId)).thenReturn(2L);

        BulkDeleteResponse result = service.deleteBulk(List.of(clinicId));

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.failed().get(clinicId)).contains("2 booking attempt(s)");
        verify(clinicRepository, never()).delete(clinic);
    }

    @Test
    void suspiciousActivityFlagsBlockDeletionAsRealActivity() {
        when(suspiciousActivityFlagRepository.countByClinic_Id(clinicId)).thenReturn(1L);

        BulkDeleteResponse result = service.deleteBulk(List.of(clinicId));

        assertThat(result.succeeded()).isEmpty();
        assertThat(result.failed().get(clinicId)).contains("1 suspicious-activity flag(s)");
        verify(clinicRepository, never()).delete(clinic);
    }
}
