package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 018 FR-003, spec US1 AC2: a Queue/Token Session gets zero Slots from this feature. */
class SlotPreGenerationQueueModeTest extends AbstractSlotGenerationIntegrationTest {

    @Test
    void queueModeSessionGetsNoSlots() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveQueueSchedule(clinic, doctor);
        LocalDate runDate = LocalDate.of(2026, 9, 1); // a Tuesday

        sessionGenerationService.generate(runDate);

        var sessions = sessionRepository.findBySchedule_Id(schedule.getId());
        assertThat(sessions).isNotEmpty();
        assertThat(slotRepository.findBySession_Id(sessions.get(0).getId())).isEmpty();
    }
}
