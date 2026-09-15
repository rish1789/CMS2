package com.cms.booking.integration;

import com.cms.scheduling.Session;
import com.cms.scheduling.Slot;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 030: extends 029's fixture directly (this codebase's established
 * {@code com.cms.booking} test-fixture inheritance pattern), adding one helper for
 * constructing a Queue Slot with an explicit, controllable {@code createdAt} - 029's own
 * {@code addQueueSlot} always uses "now," which isn't precise enough for cutoff testing.
 * {@code Slot.createdAt} has no production setter (it's a creation timestamp, not meant to be
 * mutable) - a direct SQL update, test-only, is used instead of adding one just for this.
 */
public abstract class AbstractPartialSessionCancellationIntegrationTest extends AbstractSessionCancellationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PersistenceContext
    private EntityManager entityManager;

    protected Slot addQueueSlotWithCreatedAt(Session session, int tokenNumber, Instant createdAt) {
        Slot slot = slotRepository.saveAndFlush(new Slot(session, tokenNumber));
        jdbcTemplate.update("UPDATE slot SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), slot.getId());
        // The raw JDBC update above bypasses Hibernate's persistence context, so the managed
        // `slot` reference (and a plain findById in the same context) would otherwise still
        // show the pre-update value - refresh forces a re-read from the database.
        entityManager.refresh(slot);
        return slot;
    }
}
