package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.exception.SessionNotAcceptingBookingsException;
import com.cms.booking.service.SessionCancellationService;
import com.cms.booking.service.StaffQueueBookingService;
import com.cms.identity.account.domain.Account;
import com.cms.identity.account.domain.RoleAssignment;
import com.cms.identity.clinic.Clinic;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.exception.NotAQueueSessionException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 067 US3 (FR-004, SC-005): requests that must be refused - a cancelled session, a Fixed-Time
 * session on the queue path - get their existing refusals (SESSION_NOT_ACCEPTING_BOOKINGS,
 * NOT_A_QUEUE_SESSION) even when submitted among a burst of valid bookings, and every valid
 * booking still succeeds with tokens exactly 1..N.
 */
class QueueIssuanceRefusalUnderConcurrencyTest extends AbstractQueueBookingIntegrationTest {

    private static final int VALID = 8;

    @Autowired
    private SessionCancellationService sessionCancellationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Runs before the superclass cleanup: session_cancellation references session. */
    @AfterEach
    void cleanSessionCancellations() {
        jdbcTemplate.execute("DELETE FROM session_cancellation");
    }

    @Test
    void invalidRequestsKeepTheirRefusalsWhileValidOnesAllSucceed() throws Exception {
        Clinic clinic = saveClinic();
        var doctor = saveDoctorStaffedAt(clinic);
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Session accepting = saveQueueSessionOn(clinic, doctor, tomorrow);
        Session cancelled = saveQueueSessionOn(clinic, saveDoctorStaffedAt(clinic), tomorrow);
        Session fixedTime = saveFixedTimeSessionOn(clinic, saveDoctorStaffedAt(clinic), tomorrow);
        var type = saveAppointmentTypeWithOverride(doctor, new BigDecimal("300.00"));
        var cancelledType = saveAppointmentTypeWithOverride(cancelled.getDoctorProfile(), new BigDecimal("300.00"));
        var fixedTimeType = saveAppointmentTypeWithOverride(fixedTime.getDoctorProfile(), new BigDecimal("300.00"));
        UUID staff = clinicAdminAccountId(clinic);
        sessionCancellationService.cancelSession(cancelled, staff);

        List<Callable<Object>> tasks = new ArrayList<>();
        IntStream.range(0, VALID).forEach(i -> tasks.add(() -> book(staff, clinic, accepting, type.getId())));
        tasks.add(() -> book(staff, clinic, cancelled, cancelledType.getId()));
        tasks.add(() -> book(staff, clinic, fixedTime, fixedTimeType.getId()));

        List<Object> outcomes = new ArrayList<>();
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
        try {
            for (Future<Object> f : executor.invokeAll(tasks)) {
                try {
                    outcomes.add(f.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
        } finally {
            executor.shutdown();
        }

        assertThat(outcomes.subList(0, VALID)).allSatisfy(o -> assertThat(o).isInstanceOf(Integer.class));
        assertThat(outcomes.subList(0, VALID)).containsExactlyInAnyOrderElementsOf(
                IntStream.rangeClosed(1, VALID).boxed().map(Object.class::cast).toList());
        assertThat(outcomes.get(VALID)).isInstanceOf(SessionNotAcceptingBookingsException.class);
        assertThat(outcomes.get(VALID + 1)).isInstanceOf(NotAQueueSessionException.class);
        assertThat(slotRepository.findBySession_Id(cancelled.getId())).isEmpty();
    }

    private Integer book(UUID staff, Clinic clinic, Session session, UUID typeId) {
        return staffQueueBookingService.bookSlot(staff, clinic.getId(), session.getId(),
                        new StaffQueueBookingService.BookSlotInput(saveExistingPatient(clinic).getId(), null, null, typeId))
                .getSlot()
                .getTokenNumber();
    }

    private UUID clinicAdminAccountId(Clinic clinic) {
        String unique = UUID.randomUUID().toString();
        Account admin = accountRepository.save(new Account(
                "Admin X", "adminx-" + unique + "@example.com",
                passwordEncoder.encode("Str0ng!Pass"), "CAX-" + unique.substring(0, 13), null));
        roleAssignmentRepository.save(new RoleAssignment(admin, clinic, RoleAssignment.Role.ClinicAdmin));
        return admin.getId();
    }
}
