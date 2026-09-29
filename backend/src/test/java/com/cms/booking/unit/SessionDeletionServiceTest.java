package com.cms.booking.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cms.booking.exception.SessionDeletionBlockedException;
import com.cms.booking.repository.BookingRepository;
import com.cms.booking.service.SessionDeletionService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.repository.SessionCancellationRepository;
import com.cms.scheduling.repository.SessionRepository;
import com.cms.scheduling.repository.SlotRepository;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 065-phase1-stabilization (v41-design-review §8-H item 7): a cancelled session is real activity.
 * Deleting it would drop the cancellation, and the nightly generation run would recreate the
 * session as bookable - so it is refused (and a schedule deletion detaches it instead).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionDeletionServiceTest {

    @Mock SessionRepository sessionRepository;
    @Mock SlotRepository slotRepository;
    @Mock BookingRepository bookingRepository;
    @Mock WaitlistEntryRepository waitlistEntryRepository;
    @Mock SessionCancellationRepository sessionCancellationRepository;

    private Session session;

    @BeforeEach
    void anUnusedSession() {
        session = mock(Session.class);
        when(session.getId()).thenReturn(UUID.randomUUID());
        when(slotRepository.findBySession_Id(session.getId())).thenReturn(List.of());
    }

    private SessionDeletionService service() {
        return new SessionDeletionService(
                sessionRepository, slotRepository, bookingRepository, waitlistEntryRepository, sessionCancellationRepository);
    }

    @Test
    void aNeverUsedSessionIsDeleted() {
        assertThat(service().hasRealActivity(session)).isFalse();

        service().deleteSession(session);

        verify(sessionRepository).delete(session);
    }

    @Test
    void aCancelledSessionCountsAsRealActivityAndIsNotDeleted() {
        when(sessionCancellationRepository.countBySession_Id(session.getId())).thenReturn(1L);

        assertThat(service().hasRealActivity(session)).isTrue();
        assertThatThrownBy(() -> service().deleteSession(session))
                .isInstanceOf(SessionDeletionBlockedException.class)
                .hasMessageContaining("1 cancellation");

        verify(sessionRepository, never()).delete(any());
    }
}
