package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.scheduling.domain.SlotStatus;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

/**
 * 057-day-sheet-status-overhaul: a Slot already marked APPEARED must never be picked up by the
 * automatic No-Show sweep, exactly as leaving BOOKED status already exempts it today (FR-002) -
 * mirrors NoShowDetectionTest's own fixture/assertion shape.
 */
class SlotAppearedRemovesNoShowEligibilityTest extends AbstractNoShowDetectionIntegrationTest {

    @Test
    void anAppearedSlotPastTheGracePeriodIsNeverMarkedNoShow() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var slot = saveFixedTimeSlotAt(clinic, doctor, LocalTime.now().minusMinutes(30), SlotStatus.APPEARED);

        int marked = noShowDetectionService.detectAndMarkNoShows();

        assertThat(marked).isEqualTo(0);
        assertThat(slotRepository.findById(slot.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.APPEARED);
    }
}
