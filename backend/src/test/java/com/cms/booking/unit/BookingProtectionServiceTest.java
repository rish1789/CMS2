package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingStatus;
import com.cms.booking.domain.ClinicBookingLimitOverride;
import com.cms.booking.exception.BookingLimitReachedException;
import com.cms.booking.exception.RateLimitedException;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.booking.service.BookingAttemptRecorder;
import com.cms.booking.service.BookingProtectionService;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 060-booking-abuse-prevention: covers both `checkBookingLimit` (US1) and `checkRateLimit` (US2)
 * through the single public `checkAndRecordAttempt` entry point (research.md Decision 6 - the
 * fixed check order, rate-limit first, is itself part of what's under test).
 */
@ExtendWith(MockitoExtension.class)
class BookingProtectionServiceTest {

    @Mock
    private BookingAttemptLogRepository attemptLogRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private PatientAccountRepository patientAccountRepository;

    @Mock
    private ClinicBookingLimitOverrideRepository clinicBookingLimitOverrideRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private ProtectionSettingService protectionSettingService;

    @Mock
    private BookingAttemptRecorder attemptRecorder;

    private BookingProtectionService newService() {
        return new BookingProtectionService(
                attemptLogRepository,
                bookingRepository,
                patientAccountRepository,
                clinicBookingLimitOverrideRepository,
                clinicRepository,
                protectionSettingService,
                attemptRecorder);
    }

    private void rateLimitPasses() {
        when(protectionSettingService.isRateLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getRateLimitMaxAttempts()).thenReturn(8);
        when(protectionSettingService.getRateLimitWindowMinutes()).thenReturn(10);
        when(protectionSettingService.getRateLimitCooldownMinutes()).thenReturn(15);
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.countByPatientAccount_IdAndAttemptedAtAfter(any(), any())).thenReturn(0L);
    }

    // ---- checkBookingLimit (US1) ----

    @Test
    void rejectsAtOrOverTheGlobalLimit() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        rateLimitPasses();
        when(protectionSettingService.isBookingLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        when(bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE))
                .thenReturn(15L);

        assertThatThrownBy(() -> newService().checkAndRecordAttempt(patientAccountId, clinicId))
                .isInstanceOf(BookingLimitReachedException.class);
    }

    @Test
    void allowsUnderTheGlobalLimit() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        rateLimitPasses();
        when(protectionSettingService.isBookingLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        when(bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE))
                .thenReturn(14L);
        when(clinicBookingLimitOverrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.empty());

        newService().checkAndRecordAttempt(patientAccountId, clinicId);
        // no exception - success
    }

    @Test
    void aPerClinicOverrideIsAndedWithTheGlobalLimit() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        rateLimitPasses();
        when(protectionSettingService.isBookingLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        // Under the global limit...
        when(bookingRepository.countByPatient_PatientAccount_IdAndStatus(patientAccountId, BookingStatus.ACTIVE))
                .thenReturn(3L);
        // ...but at this clinic's own, stricter override.
        ClinicBookingLimitOverride override = mock(ClinicBookingLimitOverride.class);
        when(override.getMaxActiveAppointments()).thenReturn(2);
        when(clinicBookingLimitOverrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.of(override));
        when(bookingRepository.countByPatient_PatientAccount_IdAndStatusAndPatient_Clinic_Id(
                        patientAccountId, BookingStatus.ACTIVE, clinicId))
                .thenReturn(2L);

        assertThatThrownBy(() -> newService().checkAndRecordAttempt(patientAccountId, clinicId))
                .isInstanceOf(BookingLimitReachedException.class);
    }

    @Test
    void bookingLimitDisabledSkipsTheCheckEntirely() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        rateLimitPasses();
        when(protectionSettingService.isBookingLimitEnabled()).thenReturn(false);

        newService().checkAndRecordAttempt(patientAccountId, clinicId);

        verify(bookingRepository, never()).countByPatient_PatientAccount_IdAndStatus(any(), any());
    }

    // ---- checkRateLimit (US2) ----

    @Test
    void rejectsOnceTheAttemptThresholdIsExceededWithinTheWindow() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(protectionSettingService.isRateLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getRateLimitMaxAttempts()).thenReturn(8);
        when(protectionSettingService.getRateLimitWindowMinutes()).thenReturn(10);
        when(protectionSettingService.getRateLimitCooldownMinutes()).thenReturn(15);
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        any(), any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.countByPatientAccount_IdAndAttemptedAtAfter(any(), any())).thenReturn(8L);

        assertThatThrownBy(() -> newService().checkAndRecordAttempt(patientAccountId, clinicId))
                .isInstanceOf(RateLimitedException.class);
    }

    @Test
    void aFurtherAttemptDuringAnActiveCooldownDoesNotExtendItsEndTime() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(protectionSettingService.isRateLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getRateLimitCooldownMinutes()).thenReturn(15);
        Instant originalTrigger = Instant.now().minus(5, ChronoUnit.MINUTES);
        BookingAttemptLog triggerRow = mock(BookingAttemptLog.class);
        when(triggerRow.getAttemptedAt()).thenReturn(originalTrigger);
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        any(), any(), any()))
                .thenReturn(Optional.of(triggerRow));

        RateLimitedException ex = (RateLimitedException) org.junit.jupiter.api.Assertions.assertThrows(
                RateLimitedException.class, () -> newService().checkAndRecordAttempt(patientAccountId, clinicId));

        // Cooldown end is anchored to originalTrigger + 15min, not "now" + 15min.
        long expectedRemaining =
                ChronoUnit.SECONDS.between(Instant.now(), originalTrigger.plus(15, ChronoUnit.MINUTES));
        assertThat(ex.getRetryAfterSeconds()).isCloseTo(expectedRemaining, org.assertj.core.data.Offset.offset(2L));
    }

    @Test
    void rateLimitIsEvaluatedBeforeBookingLimitAPatientInCooldownNeverReachesTheLimitCheck() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(protectionSettingService.isRateLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getRateLimitCooldownMinutes()).thenReturn(15);
        BookingAttemptLog triggerRow = mock(BookingAttemptLog.class);
        when(triggerRow.getAttemptedAt()).thenReturn(Instant.now());
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeNotOrderByAttemptedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
        when(attemptLogRepository.findFirstByPatientAccount_IdAndOutcomeAndAttemptedAtAfterOrderByAttemptedAtAsc(
                        any(), any(), any()))
                .thenReturn(Optional.of(triggerRow));

        assertThatThrownBy(() -> newService().checkAndRecordAttempt(patientAccountId, clinicId))
                .isInstanceOf(RateLimitedException.class);

        verify(protectionSettingService, never()).isBookingLimitEnabled();
    }

    @Test
    void rateLimitDisabledSkipsTheCheckEntirely() {
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(protectionSettingService.isRateLimitEnabled()).thenReturn(false);
        when(protectionSettingService.isBookingLimitEnabled()).thenReturn(true);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        when(bookingRepository.countByPatient_PatientAccount_IdAndStatus(any(), eq(BookingStatus.ACTIVE)))
                .thenReturn(0L);
        when(clinicBookingLimitOverrideRepository.findByClinic_Id(any())).thenReturn(Optional.empty());

        newService().checkAndRecordAttempt(patientAccountId, clinicId);

        verify(attemptLogRepository, never()).countByPatientAccount_IdAndAttemptedAtAfter(any(), any());
    }
}
