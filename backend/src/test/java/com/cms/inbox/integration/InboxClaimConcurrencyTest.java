package com.cms.inbox.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.AppointmentType;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.inbox.AlreadyClaimedException;
import com.cms.inbox.InboxItem;
import com.cms.inbox.InboxItemService;
import com.cms.scheduling.Session;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** 038 US2, T027/SC-002: exactly one of several concurrent claim attempts against the same item succeeds. */
class InboxClaimConcurrencyTest extends AbstractInboxIntegrationTest {

    @Autowired
    private InboxItemService inboxItemService;

    @Test
    void concurrentClaimsOnTheSameItemOnlyOneSucceeds() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session session = saveFixedTimeSessionWithSlots(clinic, doctor);
        AppointmentType appointmentType = saveAppointmentType(doctor);
        insertWalkIn(operationsAccountId(clinic), clinic, session, appointmentType);
        InboxItem item = outstandingItemsOf(clinic).get(0);

        int attempts = 10;
        List<UUID> claimants = java.util.stream.IntStream.range(0, attempts)
                .mapToObj(i -> operationsAccountId(clinic))
                .toList();

        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Boolean>> tasks = claimants.stream()
                    .<Callable<Boolean>>map(accountId -> () -> {
                        try {
                            inboxItemService.claim(clinic.getId(), item.getId(), accountId);
                            return true;
                        } catch (AlreadyClaimedException e) {
                            return false;
                        }
                    })
                    .toList();

            List<Future<Boolean>> futures = executor.invokeAll(tasks);
            long successCount = futures.stream().filter(f -> {
                        try {
                            return f.get();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .count();

            assertThat(successCount).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }
}
