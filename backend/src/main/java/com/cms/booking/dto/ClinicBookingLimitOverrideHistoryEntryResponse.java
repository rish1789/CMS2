package com.cms.booking.dto;

import com.cms.booking.domain.ClinicBookingLimitOverrideChangeLog;
import java.time.Instant;

/** 060-booking-abuse-prevention (contracts/booking-protection.md #3, AUD-003/AUD-004): one row of the override's full change history. */
public record ClinicBookingLimitOverrideHistoryEntryResponse(
        Integer previousMaxActiveAppointments, Integer newMaxActiveAppointments, Instant changedAt, String changedBy) {

    public static ClinicBookingLimitOverrideHistoryEntryResponse of(ClinicBookingLimitOverrideChangeLog log) {
        return new ClinicBookingLimitOverrideHistoryEntryResponse(
                log.getPreviousMaxActiveAppointments(), log.getNewMaxActiveAppointments(), log.getChangedAt(), log.getChangedBy());
    }
}
