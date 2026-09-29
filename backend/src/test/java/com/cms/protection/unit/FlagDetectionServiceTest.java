package com.cms.protection.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.BookingAttemptLog;
import com.cms.booking.domain.BookingAttemptOutcome;
import com.cms.booking.repository.BookingAttemptLogRepository;
import com.cms.booking.repository.BookingRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.patient.account.domain.PatientAccount;
import com.cms.patient.account.repository.PatientAccountRepository;
import com.cms.protection.domain.SuspiciousActivityFlag;
import com.cms.protection.repository.SuspiciousActivityFlagRepository;
import com.cms.protection.service.FlagDetectionService;
import com.cms.protection.service.ProtectionSettingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 060-booking-abuse-prevention (research.md Decision 4): this module's first `@Scheduled`
 * service test. Each test isolates exactly one signal by mocking only that signal's data source
 * to a triggering value, leaving every other signal's source empty (a safe no-op).
 */
@ExtendWith(MockitoExtension.class)
class FlagDetectionServiceTest {

    @Mock
    private BookingAttemptLogRepository attemptLogRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private SuspiciousActivityFlagRepository flagRepository;

    @Mock
    private PatientAccountRepository patientAccountRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private ProtectionSettingService protectionSettingService;

    private FlagDetectionService newService() {
        return new FlagDetectionService(
                attemptLogRepository, bookingRepository, flagRepository, patientAccountRepository, clinicRepository, protectionSettingService);
    }

    /** Every signal's own data source defaults to empty - each test overrides exactly one. */
    private void allSourcesEmpty() {
        when(protectionSettingService.isFlaggingEnabled()).thenReturn(true);
        when(attemptLogRepository.countAttemptsByPatientAndClinicSince(any())).thenReturn(List.of());
        when(bookingRepository.countCancellationsByPatientAndClinicSince(any())).thenReturn(List.of());
        when(bookingRepository.countNoShowsByPatientAndClinicSince(any())).thenReturn(List.of());
        when(bookingRepository.findActiveFixedTimeSchedulesSince(any())).thenReturn(List.of());
        when(attemptLogRepository.findRateLimitedSince(any())).thenReturn(List.of());
    }

    @Test
    void flaggingDisabledRunsNoSignalsAtAll() {
        when(protectionSettingService.isFlaggingEnabled()).thenReturn(false);

        newService().runSweep();

        verify(attemptLogRepository, never()).countAttemptsByPatientAndClinicSince(any());
        verify(flagRepository, never()).save(any());
    }

    @Test
    void highAttemptVolumeFiresAtThresholdAndNotBefore() {
        allSourcesEmpty();
        when(protectionSettingService.getHighAttemptVolumeThreshold()).thenReturn(10);
        when(protectionSettingService.getHighAttemptVolumeWindowMinutes()).thenReturn(60);
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        var row = mock(BookingAttemptLogRepository.PatientClinicCount.class);
        when(row.getPatientAccountId()).thenReturn(patientAccountId);
        when(row.getClinicId()).thenReturn(clinicId);
        when(row.getCount()).thenReturn(10L);
        when(attemptLogRepository.countAttemptsByPatientAndClinicSince(any())).thenReturn(List.of(row));
        when(flagRepository.findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(patientAccountRepository.getReferenceById(patientAccountId)).thenReturn(mock(PatientAccount.class));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().runSweep();

        verify(flagRepository).save(any());
    }

    @Test
    void highAttemptVolumeDoesNotFireBelowThreshold() {
        allSourcesEmpty();
        when(protectionSettingService.getHighAttemptVolumeThreshold()).thenReturn(10);
        when(protectionSettingService.getHighAttemptVolumeWindowMinutes()).thenReturn(60);
        var row = mock(BookingAttemptLogRepository.PatientClinicCount.class);
        when(row.getCount()).thenReturn(9L);
        when(attemptLogRepository.countAttemptsByPatientAndClinicSince(any())).thenReturn(List.of(row));

        newService().runSweep();

        verify(flagRepository, never()).save(any());
    }

    @Test
    void repeatedCancellationsFiresAtThreshold() {
        allSourcesEmpty();
        when(protectionSettingService.getRepeatedCancellationsThreshold()).thenReturn(4);
        when(protectionSettingService.getRepeatedCancellationsWindowDays()).thenReturn(30);
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        var row = mock(BookingRepository.PatientClinicCount.class);
        when(row.getPatientAccountId()).thenReturn(patientAccountId);
        when(row.getClinicId()).thenReturn(clinicId);
        when(row.getCount()).thenReturn(4L);
        when(bookingRepository.countCancellationsByPatientAndClinicSince(any())).thenReturn(List.of(row));
        when(flagRepository.findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(patientAccountRepository.getReferenceById(patientAccountId)).thenReturn(mock(PatientAccount.class));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().runSweep();

        verify(flagRepository).save(any());
    }

    @Test
    void repeatedNoShowsFiresAtThreshold() {
        allSourcesEmpty();
        when(protectionSettingService.getRepeatedNoShowsThreshold()).thenReturn(3);
        when(protectionSettingService.getRepeatedNoShowsWindowDays()).thenReturn(90);
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        var row = mock(BookingRepository.PatientClinicCount.class);
        when(row.getPatientAccountId()).thenReturn(patientAccountId);
        when(row.getClinicId()).thenReturn(clinicId);
        when(row.getCount()).thenReturn(3L);
        when(bookingRepository.countNoShowsByPatientAndClinicSince(any())).thenReturn(List.of(row));
        when(flagRepository.findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(patientAccountRepository.getReferenceById(patientAccountId)).thenReturn(mock(PatientAccount.class));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().runSweep();

        verify(flagRepository).save(any());
    }

    @Test
    void overlappingAppointmentsFiresWhenEnoughOfAPatientsOwnBookingsOverlap() {
        allSourcesEmpty();
        when(protectionSettingService.getOverlappingAppointmentsThreshold()).thenReturn(3);
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        List<BookingRepository.ActiveBookingSchedule> schedules = List.of(
                schedule(patientAccountId, clinicId, date, LocalTime.of(9, 0), LocalTime.of(9, 30)),
                schedule(patientAccountId, clinicId, date, LocalTime.of(9, 15), LocalTime.of(9, 45)),
                schedule(patientAccountId, clinicId, date, LocalTime.of(9, 20), LocalTime.of(9, 50)));
        when(bookingRepository.findActiveFixedTimeSchedulesSince(any())).thenReturn(schedules);
        when(flagRepository.findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        when(patientAccountRepository.getReferenceById(patientAccountId)).thenReturn(mock(PatientAccount.class));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().runSweep();

        verify(flagRepository).save(any());
    }

    @Test
    void repeatedRateLimitViolationsFiresOnDistinctEpisodesNotRawRowCount() {
        allSourcesEmpty();
        when(protectionSettingService.getRepeatedRateLimitViolationsThreshold()).thenReturn(3);
        when(protectionSettingService.getRepeatedRateLimitViolationsWindowHours()).thenReturn(24);
        when(protectionSettingService.getRateLimitCooldownMinutes()).thenReturn(15);
        UUID patientAccountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        PatientAccount patientAccount = mock(PatientAccount.class);
        when(patientAccount.getId()).thenReturn(patientAccountId);
        Clinic clinic = mock(Clinic.class);
        when(clinic.getId()).thenReturn(clinicId);
        Instant base = Instant.now().minus(1, ChronoUnit.HOURS);
        // Two rows very close together (same episode) + one far apart (a second, distinct episode) - only 2 episodes total, below threshold 3.
        BookingAttemptLog row1 = attemptLog(patientAccount, clinic, base);
        BookingAttemptLog row2 = attemptLog(patientAccount, clinic, base.plus(1, ChronoUnit.MINUTES));
        BookingAttemptLog row3 = attemptLog(patientAccount, clinic, base.plus(30, ChronoUnit.MINUTES));
        when(attemptLogRepository.findRateLimitedSince(any())).thenReturn(List.of(row1, row2, row3));

        newService().runSweep();

        verify(flagRepository, never()).save(any());
    }

    @Test
    void aSignalWithAnAlreadyOutstandingFlagDoesNotCreateADuplicate() {
        allSourcesEmpty();
        when(protectionSettingService.getHighAttemptVolumeThreshold()).thenReturn(10);
        when(protectionSettingService.getHighAttemptVolumeWindowMinutes()).thenReturn(60);
        var row = mock(BookingAttemptLogRepository.PatientClinicCount.class);
        when(row.getPatientAccountId()).thenReturn(UUID.randomUUID());
        when(row.getClinicId()).thenReturn(UUID.randomUUID());
        when(row.getCount()).thenReturn(10L);
        when(attemptLogRepository.countAttemptsByPatientAndClinicSince(any())).thenReturn(List.of(row));
        when(flagRepository.findByPatientAccount_IdAndClinic_IdAndSignalTypeAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.of(mock(SuspiciousActivityFlag.class)));

        newService().runSweep();

        verify(flagRepository, never()).save(any());
    }

    private static BookingRepository.ActiveBookingSchedule schedule(
            UUID patientAccountId, UUID clinicId, LocalDate date, LocalTime start, LocalTime end) {
        BookingRepository.ActiveBookingSchedule s = mock(BookingRepository.ActiveBookingSchedule.class);
        when(s.getPatientAccountId()).thenReturn(patientAccountId);
        when(s.getClinicId()).thenReturn(clinicId);
        when(s.getSessionDate()).thenReturn(date);
        when(s.getStartTime()).thenReturn(start);
        when(s.getEndTime()).thenReturn(end);
        return s;
    }

    private static BookingAttemptLog attemptLog(PatientAccount patientAccount, Clinic clinic, Instant attemptedAt) {
        return new BookingAttemptLog(patientAccount, clinic, attemptedAt, BookingAttemptOutcome.RATE_LIMITED, null);
    }
}
