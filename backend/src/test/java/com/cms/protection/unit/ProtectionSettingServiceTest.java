package com.cms.protection.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.protection.domain.ProtectionSetting;
import com.cms.protection.exception.InvalidSettingValueException;
import com.cms.protection.exception.ProtectionSettingNotFoundException;
import com.cms.protection.exception.UnrecognizedSettingException;
import com.cms.protection.repository.ProtectionSettingChangeLogRepository;
import com.cms.protection.repository.ProtectionSettingRepository;
import com.cms.protection.service.ProtectionSettingService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 060-booking-abuse-prevention (research.md Decision 5): this module's first unit tier.
 * Covers the fallback-to-default read pattern and the write-time validation/change-log append.
 */
@ExtendWith(MockitoExtension.class)
class ProtectionSettingServiceTest {

    @Mock
    private ProtectionSettingRepository settingRepository;

    @Mock
    private ProtectionSettingChangeLogRepository changeLogRepository;

    private ProtectionSettingService newService() {
        return new ProtectionSettingService(settingRepository, changeLogRepository);
    }

    @Test
    void returnsTheDocumentedDefaultWhenNoRowExistsForAName() {
        when(settingRepository.findByName("booking-limit.global-max-active")).thenReturn(Optional.empty());

        assertThat(newService().getGlobalMaxActiveAppointments()).isEqualTo(15);
    }

    @Test
    void returnsTheStoredValueOnceOneExists() {
        ProtectionSetting stored = mock(ProtectionSetting.class);
        when(stored.getValue()).thenReturn("25");
        when(settingRepository.findByName("booking-limit.global-max-active")).thenReturn(Optional.of(stored));

        assertThat(newService().getGlobalMaxActiveAppointments()).isEqualTo(25);
    }

    @Test
    void rejectsAWriteToAnUnrecognizedName() {
        assertThatThrownBy(() -> newService().update("not-a-real-setting", "5", "admin@example.com"))
                .isInstanceOf(UnrecognizedSettingException.class);
    }

    @Test
    void rejectsAValueThatFailsItsSettingsTypeValidation() {
        assertThatThrownBy(() -> newService().update("booking-limit.global-max-active", "not-a-number", "admin@example.com"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    void rejectsAZeroOrNegativeValueForAPositiveIntSetting() {
        assertThatThrownBy(() -> newService().update("booking-limit.global-max-active", "0", "admin@example.com"))
                .isInstanceOf(InvalidSettingValueException.class);
    }

    @Test
    void aSuccessfulFirstWriteAppendsAChangeLogRowWithNullPreviousValue() {
        when(settingRepository.findByName("booking-limit.global-max-active")).thenReturn(Optional.empty());

        newService().update("booking-limit.global-max-active", "20", "admin@example.com");

        ArgumentCaptor<com.cms.protection.domain.ProtectionSettingChangeLog> captor =
                ArgumentCaptor.forClass(com.cms.protection.domain.ProtectionSettingChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousValue()).isNull();
        assertThat(captor.getValue().getNewValue()).isEqualTo("20");
        assertThat(captor.getValue().getChangedBy()).isEqualTo("admin@example.com");
    }

    @Test
    void aSubsequentWriteAppendsAChangeLogRowWithTheCorrectPreviousValue() {
        ProtectionSetting stored = mock(ProtectionSetting.class);
        when(stored.getValue()).thenReturn("15");
        when(settingRepository.findByName("booking-limit.global-max-active")).thenReturn(Optional.of(stored));

        newService().update("booking-limit.global-max-active", "20", "admin@example.com");

        ArgumentCaptor<com.cms.protection.domain.ProtectionSettingChangeLog> captor =
                ArgumentCaptor.forClass(com.cms.protection.domain.ProtectionSettingChangeLog.class);
        verify(changeLogRepository).save(captor.capture());
        assertThat(captor.getValue().getPreviousValue()).isEqualTo("15");
        assertThat(captor.getValue().getNewValue()).isEqualTo("20");
    }

    @Test
    void listAllMarksAnUnstoredSettingAsDefault() {
        when(settingRepository.findByName(any())).thenReturn(Optional.empty());

        var views = newService().listAll();

        assertThat(views).hasSize(16);
        assertThat(views).allMatch(ProtectionSettingService.SettingView::isDefault);
    }

    @Test
    void historyThrowsForAnUnrecognizedName() {
        assertThatThrownBy(() -> newService().history("not-a-real-setting"))
                .isInstanceOf(ProtectionSettingNotFoundException.class);
    }
}
