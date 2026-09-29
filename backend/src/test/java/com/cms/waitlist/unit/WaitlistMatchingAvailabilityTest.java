package com.cms.waitlist.unit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cms.inbox.service.InboxItemService;
import com.cms.notification.service.NotificationEventService;
import com.cms.scheduling.domain.Session;
import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.service.SessionAvailabilityService;
import com.cms.scheduling.service.SessionAvailabilityService.Verdict;
import com.cms.waitlist.repository.WaitlistEntryRepository;
import com.cms.waitlist.service.WaitlistMatchingService;
import java.time.LocalTime;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 065-phase1-stabilization (v41-design-review.md §8-H item 5): the waitlist is never offered a slot
 * the shared bookability rule would refuse - a claim would only fail, and the offer would hold the
 * entry hostage for its whole window.
 */
@ExtendWith(MockitoExtension.class)
class WaitlistMatchingAvailabilityTest {

    @Mock WaitlistEntryRepository waitlistEntryRepository;
    @Mock NotificationEventService notificationEventService;
    @Mock InboxItemService inboxItemService;
    @Mock SessionAvailabilityService availability;

    @ParameterizedTest
    @EnumSource(value = Verdict.class, names = {"PAST_DATE", "ELAPSED", "CANCELLED"})
    void anUnbookableOpenSlotIsNeverOffered(Verdict verdict) {
        Session session = mock(Session.class);
        Slot slot = new Slot(session, LocalTime.of(10, 0), LocalTime.of(10, 15));
        when(availability.evaluate(session, slot)).thenReturn(verdict);

        new WaitlistMatchingService(waitlistEntryRepository, notificationEventService, inboxItemService, availability)
                .matchAndOffer(session, slot);

        verify(availability).evaluate(any(), any());
        verifyNoInteractions(waitlistEntryRepository, notificationEventService, inboxItemService);
    }
}
