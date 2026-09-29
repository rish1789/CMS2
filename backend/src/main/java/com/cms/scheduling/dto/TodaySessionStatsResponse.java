package com.cms.scheduling.dto;

import com.cms.scheduling.domain.SlotStatus;
import com.cms.scheduling.repository.SlotRepository;
import java.util.List;

/**
 * 051-staff-dashboard-enhancement T008: folds SlotRepository.countStatusByClinicAndDate's
 * grouped rows into the 2 counts the dashboard's "Today's stats" tile needs. A status with no
 * matching row today (GROUP BY produces no row for it at all) defaults to 0.
 */
public record TodaySessionStatsResponse(int completedCount, int noShowCount) {

    public static TodaySessionStatsResponse from(List<SlotRepository.SlotStatusCount> rows) {
        int completedCount = 0;
        int noShowCount = 0;
        for (SlotRepository.SlotStatusCount row : rows) {
            if (row.getStatus() == SlotStatus.COMPLETED) {
                completedCount = (int) row.getCount();
            } else if (row.getStatus() == SlotStatus.NO_SHOW) {
                noShowCount = (int) row.getCount();
            }
        }
        return new TodaySessionStatsResponse(completedCount, noShowCount);
    }
}
