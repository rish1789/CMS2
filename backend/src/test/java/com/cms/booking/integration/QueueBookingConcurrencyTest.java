package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.StaffQueueBookingService;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 022 FR-003, spec SC-004: proves QueueSlotService's retry closure still functions correctly
 * when called the way StaffQueueBookingService.bookSlot calls it (research.md) - every
 * concurrent booking succeeds with a distinct, never-reused token number.
 */
class QueueBookingConcurrencyTest extends AbstractQueueBookingIntegrationTest {

    @Test
    void concurrentQueueBookingsAgainstOneSessionEachGetADistinctNeverReusedToken() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSession(clinic, doctor);
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var callerAccountId = clinicAdminAccountId(clinic);

        int attempts = 10;
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Callable<Integer>> tasks = IntStream.range(0, attempts)
                    .<Callable<Integer>>mapToObj(i -> () -> {
                        var patient = saveExistingPatient(clinic);
                        var booking = staffQueueBookingService.bookSlot(
                                callerAccountId,
                                clinic.getId(),
                                session.getId(),
                                new StaffQueueBookingService.BookSlotInput(
                                        patient.getId(), null, null, appointmentType.getId()));
                        return booking.getSlot().getTokenNumber();
                    })
                    .toList();

            List<Future<Integer>> futures = executor.invokeAll(tasks);
            List<Integer> tokens = futures.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            assertThat(tokens).hasSize(attempts);
            assertThat(tokens).doesNotHaveDuplicates();
            assertThat(bookingRepository.findAll()).hasSize(attempts);
        } finally {
            executor.shutdown();
        }
    }

    private java.util.UUID clinicAdminAccountId(com.cms.identity.clinic.Clinic clinic) {
        String unique = java.util.UUID.randomUUID().toString();
        var admin = accountRepository.save(new com.cms.identity.account.Account(
                "Admin X", "adminx-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique, null));
        roleAssignmentRepository.save(new com.cms.identity.account.RoleAssignment(
                admin, clinic, com.cms.identity.account.RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
