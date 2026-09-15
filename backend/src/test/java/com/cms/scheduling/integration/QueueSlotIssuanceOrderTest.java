package com.cms.scheduling.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 019 FR-001/FR-002/FR-003, spec US1 AC1-AC3: sequential issuance, never-reused numbering. */
class QueueSlotIssuanceOrderTest extends AbstractQueueSlotIntegrationTest {

    @Test
    void sequentialCallsIssueTokensInOrderStartingAtOne() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);

        for (int expectedToken = 1; expectedToken <= 5; expectedToken++) {
            var slot = queueSlotService.issueNextSlot(session.getId());
            assertThat(slot.getTokenNumber()).isEqualTo(expectedToken);
            assertThat(slot.getStartTime()).isNull();
            assertThat(slot.getEndTime()).isNull();
        }
    }

    @Test
    void nextIssuanceAfterASkippedTokenContinuesFromTheHighestEverIssued() {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);

        for (int i = 1; i <= 5; i++) {
            queueSlotService.issueNextSlot(session.getId());
        }
        // Token 3's Slot is simulated-cancelled here only in the sense that nothing about
        // it changes - this feature has no cancellation mechanism (spec Scope Decisions).
        // The guarantee under test is purely: the next issuance is 6, not a "reused" 3.

        var next = queueSlotService.issueNextSlot(session.getId());

        assertThat(next.getTokenNumber()).isEqualTo(6);
    }
}
