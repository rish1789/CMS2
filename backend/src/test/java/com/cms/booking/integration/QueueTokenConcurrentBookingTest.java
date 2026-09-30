package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.Booking;
import com.cms.booking.service.PatientQueueBookingService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.patient.account.domain.PatientAccount;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * 067 US1 scenario 2 (FR-001-FR-003): patient self-service and staff-assisted queue bookings for
 * one session, submitted at the same moment, all succeed with tokens exactly 1..N. More callers
 * (20) than the test connection pool (10), so a design that needs a second connection while
 * holding the session lock would stall here.
 */
class QueueTokenConcurrentBookingTest extends AbstractQueueBookingIntegrationTest {

    private static final int PER_PATH = 10;

    @Test
    void simultaneousPatientAndStaffQueueBookingsAllGetDistinctSequentialTokens() throws Exception {
        var clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        var session = saveQueueSessionOn(clinic, doctor, LocalDate.now().plusDays(1));
        var appointmentType = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        UUID staffCallerId = clinicAdminAccountId(clinic);
        // Distinct patient accounts, so 060's per-patient rate limit and booking limit are never hit.
        List<PatientAccount> accounts = IntStream.range(0, PER_PATH).mapToObj(i -> savePatientAccount()).toList();

        List<Callable<Booking>> tasks = new ArrayList<>();
        for (PatientAccount account : accounts) {
            tasks.add(() -> patientQueueBookingService.bookSlot(
                    account.getId(), clinic.getId(), session.getId(),
                    new PatientQueueBookingService.BookSlotInput("Patient " + account.getId(), appointmentType.getId())));
        }
        for (int i = 0; i < PER_PATH; i++) {
            tasks.add(() -> staffQueueBookingService.bookSlot(
                    staffCallerId, clinic.getId(), session.getId(),
                    new StaffQueueBookingService.BookSlotInput(
                            saveExistingPatient(clinic).getId(), null, null, appointmentType.getId())));
        }

        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        List<Integer> tokens = new ArrayList<>();
        try {
            for (Future<Booking> future : executor.invokeAll(tasks)) {
                tokens.add(future.get().getSlot().getTokenNumber());
            }
        } finally {
            executor.shutdown();
        }

        assertThat(tokens).containsExactlyInAnyOrderElementsOf(IntStream.rangeClosed(1, 2 * PER_PATH).boxed().toList());
        assertThat(bookingRepository.findAll()).hasSize(2 * PER_PATH);
    }

    private UUID clinicAdminAccountId(com.cms.identity.clinic.Clinic clinic) {
        String unique = UUID.randomUUID().toString();
        Account admin = accountRepository.save(new Account(
                "Admin X", "adminx-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique.substring(0, 13), null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
