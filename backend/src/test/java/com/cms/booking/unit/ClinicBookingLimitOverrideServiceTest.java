package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.domain.ClinicBookingLimitOverride;
import com.cms.booking.domain.ClinicBookingLimitOverrideChangeLog;
import com.cms.booking.exception.ClinicLimitExceedsGlobalCapException;
import com.cms.booking.exception.ClinicProtectionForbiddenException;
import com.cms.booking.repository.ClinicBookingLimitOverrideChangeLogRepository;
import com.cms.booking.repository.ClinicBookingLimitOverrideRepository;
import com.cms.booking.service.ClinicBookingLimitOverrideService;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.account.repository.RoleAssignmentRepository;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.clinic.ClinicRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 060-booking-abuse-prevention (spec.md BR-005, AUD-003): validation against the global cap, and the change-log append. */
@ExtendWith(MockitoExtension.class)
class ClinicBookingLimitOverrideServiceTest {

    @Mock
    private ClinicBookingLimitOverrideRepository overrideRepository;

    @Mock
    private ClinicBookingLimitOverrideChangeLogRepository changeLogRepository;

    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    @Mock
    private ClinicRepository clinicRepository;

    @Mock
    private ProtectionSettingService protectionSettingService;

    private ClinicBookingLimitOverrideService newService() {
        return new ClinicBookingLimitOverrideService(
                overrideRepository, changeLogRepository, roleAssignmentRepository, clinicRepository, protectionSettingService);
    }

    private void grantClinicAdmin(UUID accountId, UUID clinicId) {
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(
                accountId, clinicId, RoleAssignment.Role.ClinicAdmin))
                .thenReturn(true);
    }

    @Test
    void rejectsAWriteAboveTheCurrentGlobalCap() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        grantClinicAdmin(accountId, clinicId);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);

        assertThatThrownBy(() -> newService().update(accountId, clinicId, 20, "admin@example.com"))
                .isInstanceOf(ClinicLimitExceedsGlobalCapException.class);
    }

    @Test
    void rejectsAWriteFromACallerWithoutClinicAdminAtThisClinic() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        when(roleAssignmentRepository.existsByAccount_IdAndClinic_IdAndRoleAndActiveTrue(any(), any(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> newService().update(accountId, clinicId, 5, "admin@example.com"))
                .isInstanceOf(ClinicProtectionForbiddenException.class);
    }

    @Test
    void aFirstEverWriteAppendsAChangeLogRowWithNullPreviousValue() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        grantClinicAdmin(accountId, clinicId);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        when(overrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.empty());
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().update(accountId, clinicId, 5, "admin@example.com");

        ArgumentCaptor<ClinicBookingLimitOverrideChangeLog> captor = ArgumentCaptor.forClass(ClinicBookingLimitOverrideChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousMaxActiveAppointments()).isNull();
        assertThat(captor.getValue().getNewMaxActiveAppointments()).isEqualTo(5);
    }

    @Test
    void aSubsequentWriteAppendsAChangeLogRowWithTheCorrectPreviousValue() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        grantClinicAdmin(accountId, clinicId);
        when(protectionSettingService.getGlobalMaxActiveAppointments()).thenReturn(15);
        ClinicBookingLimitOverride existing = mock(ClinicBookingLimitOverride.class);
        when(existing.getMaxActiveAppointments()).thenReturn(5);
        when(overrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.of(existing));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().update(accountId, clinicId, 8, "admin@example.com");

        ArgumentCaptor<ClinicBookingLimitOverrideChangeLog> captor = ArgumentCaptor.forClass(ClinicBookingLimitOverrideChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousMaxActiveAppointments()).isEqualTo(5);
        assertThat(captor.getValue().getNewMaxActiveAppointments()).isEqualTo(8);
    }

    @Test
    void deletingAnExistingOverrideAppendsAChangeLogRowWithNullNewValue() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        grantClinicAdmin(accountId, clinicId);
        ClinicBookingLimitOverride existing = mock(ClinicBookingLimitOverride.class);
        when(existing.getMaxActiveAppointments()).thenReturn(5);
        when(overrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.of(existing));
        when(clinicRepository.getReferenceById(clinicId)).thenReturn(mock(Clinic.class));

        newService().delete(accountId, clinicId, "admin@example.com");

        ArgumentCaptor<ClinicBookingLimitOverrideChangeLog> captor = ArgumentCaptor.forClass(ClinicBookingLimitOverrideChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousMaxActiveAppointments()).isEqualTo(5);
        assertThat(captor.getValue().getNewMaxActiveAppointments()).isNull();
    }

    @Test
    void deletingWhenNoOverrideExistsIsANoOp() {
        UUID accountId = UUID.randomUUID();
        UUID clinicId = UUID.randomUUID();
        grantClinicAdmin(accountId, clinicId);
        when(overrideRepository.findByClinic_Id(clinicId)).thenReturn(Optional.empty());

        newService().delete(accountId, clinicId, "admin@example.com");

        verify(changeLogRepository, org.mockito.Mockito.never()).save(any());
    }
}
