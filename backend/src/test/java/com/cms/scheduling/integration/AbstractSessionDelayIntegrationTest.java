package com.cms.scheduling.integration;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import com.cms.scheduling.SlotStatus;
import java.time.LocalTime;

/**
 * 026: extends 023's no-show fixture directly (this codebase's own established
 * {@code com.cms.scheduling} test-fixture inheritance chain), reusing {@code saveFixedTimeSlotAt}
 * / {@code saveQueueSlot} / {@code saveBookingFor} as-is. Adds one new helper: no
 * {@code com.cms.scheduling} fixture has ever needed an Operations-role token before, since
 * {@code ScheduleService}'s own authorization is doctor-or-ClinicAdmin, not Operations.
 */
public abstract class AbstractSessionDelayIntegrationTest extends AbstractNoShowDetectionIntegrationTest {

    /** Mirrors {@code AbstractScheduleIntegrationTest.clinicAdminToken}'s shape - a random suffix keeps it safe under concurrent test threads. */
    protected String operationsToken(Clinic clinic) {
        String unique = java.util.UUID.randomUUID().toString();
        Account ops = accountRepository.save(new Account(
                "Ops " + unique, "ops-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "OP-" + unique, null));
        roleAssignmentRepository.save(new RoleAssignment(ops, clinic, RoleAssignment.Role.Operations));
        return staffJwtService.issueToken(ops.getId());
    }

    /** Adds one more Slot at the given scheduled time into an already-existing Session (e.g. one returned by {@code saveFixedTimeSlotAt}), in the given status. */
    protected Slot addSlotAt(Session session, LocalTime scheduledTime, SlotStatus status) {
        Slot slot = new Slot(session, scheduledTime, scheduledTime.plusMinutes(15), false);
        slot.setStatus(status);
        return slotRepository.save(slot);
    }
}
