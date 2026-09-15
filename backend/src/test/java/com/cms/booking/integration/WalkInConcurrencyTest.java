package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.identity.account.Account;
import com.cms.identity.account.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/** 025 US3 (P3), FR-010/SC-005: concurrent walk-in insertions targeting a Session with exactly one eligible Slot. */
class WalkInConcurrencyTest extends AbstractWalkInIntegrationTest {

    @Test
    void concurrentInsertionsAgainstOneEligibleSlotOnlyOneSucceeds() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveFixedTimeSessionWithSlots(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var callerAccountId = clinicAdminAccountId(clinic);

        ExecutorService executor = Executors.newFixedThreadPool(10);
        try {
            List<Callable<Boolean>> attempts = java.util.stream.IntStream.range(0, 10)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            walkInInsertionService.insertWalkIn(
                                    callerAccountId,
                                    clinic.getId(),
                                    session.getId(),
                                    new com.cms.booking.WalkInInsertionService.WalkInInsertionInput(
                                            null, "Walk-in " + i, null, appointmentType.getId(), null));
                            return true;
                        } catch (com.cms.booking.SlotAlreadyBookedException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(attempts);
            long successCount = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) {
                    successCount++;
                }
            }

            // Only the Session's single cold-start buffer Slot is OPEN at the start (012/022) - every
            // other Slot is a plain regular OPEN Slot, which would need an override reason (none
            // supplied here), so the buffer Slot is the only tier every concurrent attempt can reach.
            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private UUID clinicAdminAccountId(Clinic clinic) {
        String unique = UUID.randomUUID().toString();
        Account admin = accountRepository.save(new Account(
                "Admin X", "adminx-" + unique + "@example.com", passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique, null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
