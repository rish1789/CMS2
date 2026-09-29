package com.cms.booking.integration;

import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
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

    protected Slot addQueueSlotWithCreatedAt(Session session, int tokenNumber, Instant createdAt) {
        Slot slot = slotRepository.saveAndFlush(new Slot(session, tokenNumber));
        jdbcTemplate.update("UPDATE slot SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), slot.getId());
        // The raw JDBC update above bypasses Hibernate, so the returned `slot` still holds the
        // pre-update value. Outside a test transaction each repository call gets a fresh
        // persistence context, so a re-read returns the updated row (refresh() needs a transaction).
        return slotRepository.findById(slot.getId()).orElseThrow();
    }
}
