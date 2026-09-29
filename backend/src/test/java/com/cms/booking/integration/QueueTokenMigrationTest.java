package com.cms.booking.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.cms.booking.domain.Booking;
import com.cms.identity.clinic.Clinic;
import com.cms.identity.doctor.DoctorProfile;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 064-queue-send-in-complete (FR-011, Constitution I data migration, tasks.md T001): V40 moves an
 * active queue booking's token from the pre-064 OPEN state to waiting (BOOKED), and leaves a
 * cancelled booking's token alone. Re-runs the migration's own statement against rows shaped like
 * pre-064 data, since Flyway has already applied it to the empty schema at startup.
 */
class QueueTokenMigrationTest extends AbstractSessionCancellationIntegrationTest {

    // The repository's @Modifying update runs inside a service transaction in production;
    // a test calling it directly must supply one.
    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Slot preFeatureToken(Session queue, int token) {
        Slot slot = new Slot(queue, token);
        slot.setStatus(SlotStatus.OPEN); // how every queue token looked before 064
        return slotRepository.save(slot);
    }

    @Test
    void activeQueueBookingsTokensBecomeWaitingAndCancelledOnesAreLeftAlone() throws Exception {
        Clinic clinic = saveClinic();
        DoctorProfile doctor = saveDoctorStaffedAt(clinic);
        Session queue = saveQueueSession(clinic, doctor);
        Slot activeToken = preFeatureToken(queue, 1);
        Slot cancelledToken = preFeatureToken(queue, 2);
        bookSlot(clinic, doctor, activeToken);
        Booking cancelled = bookSlot(clinic, doctor, cancelledToken);
        transactionTemplate.executeWithoutResult(status -> bookingRepository.cancelIfActive(cancelled.getId()));
        // bookSlot flips a slot BOOKED; put both tokens back to the pre-064 shape.
        jdbcTemplate.update("UPDATE slot SET status = 'OPEN' WHERE id IN (?, ?)", activeToken.getId(), cancelledToken.getId());

        String migration = new ClassPathResource("db/migration/V40__queue_tokens_booked.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(migration);

        assertThat(slotRepository.findById(activeToken.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.BOOKED);
        assertThat(slotRepository.findById(cancelledToken.getId()).orElseThrow().getStatus()).isEqualTo(SlotStatus.OPEN);
    }
}
