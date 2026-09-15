package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cms.scheduling.NotAQueueSessionException;
import com.cms.scheduling.SessionNotFoundException;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 019 FR-004/FR-005, spec US1 AC5: rejects a Fixed-Time Session and an unknown Session, creating no Slot either way. */
class QueueSlotIssuanceRejectionTest extends AbstractQueueSlotIntegrationTest {

    @Test
    void fixedTimeSessionIsRejected() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var schedule = saveFixedTimeSchedule(clinic, doctor);
        sessionGenerationService.generate(LocalDate.now());
        var fixedTimeSession = sessionRepository.findBySchedule_Id(schedule.getId()).get(0);
        int slotCountBefore = slotRepository.findBySession_Id(fixedTimeSession.getId()).size();

        assertThatThrownBy(() -> queueSlotService.issueNextSlot(fixedTimeSession.getId()))
                .isInstanceOf(NotAQueueSessionException.class);

        assertThat(slotRepository.findBySession_Id(fixedTimeSession.getId())).hasSize(slotCountBefore);
    }

    @Test
    void unknownSessionIsRejected() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> queueSlotService.issueNextSlot(unknownId))
                .isInstanceOf(SessionNotFoundException.class);
    }
}
