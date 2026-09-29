package com.cms.scheduling.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cms.scheduling.domain.ScheduleMode;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.dto.SessionSummaryResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 065-phase1-stabilization (owner decision 3): the session list flags a whole-cancelled session. */
class SessionSummaryCancelledFlagTest {

    private Session session() {
        Session session = mock(Session.class, RETURNS_DEEP_STUBS);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(session.getMode()).thenReturn(ScheduleMode.FIXED_TIME);
        return session;
    }

    @Test
    void theCancelledFlagIsCarriedThrough() {
        assertThat(SessionSummaryResponse.from(session(), 2L, 16L, 0, false, true).cancelled()).isTrue();
        assertThat(SessionSummaryResponse.from(session(), 2L, 16L, 0, false, false).cancelled()).isFalse();
    }

    @Test
    void theShortFormIsNeverCancelled() {
        assertThat(SessionSummaryResponse.from(session(), null, null).cancelled()).isFalse();
    }
}
