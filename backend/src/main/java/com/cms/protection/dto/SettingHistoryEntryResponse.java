package com.cms.protection.dto;

import com.cms.protection.domain.ProtectionSettingChangeLog;
import java.time.Instant;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #4, AUD-002/AUD-004): one row of a setting's full change history. */
public record SettingHistoryEntryResponse(String previousValue, String newValue, Instant changedAt, String changedBy) {

    public static SettingHistoryEntryResponse of(ProtectionSettingChangeLog log) {
        return new SettingHistoryEntryResponse(log.getPreviousValue(), log.getNewValue(), log.getChangedAt(), log.getChangedBy());
    }
}
