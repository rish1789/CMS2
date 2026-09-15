package com.cms.inbox;

import com.cms.identity.account.SecurityConfig;
import com.cms.inbox.dto.InboxItemResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** contracts/inbox.md: list, live stream, and claim/release/resolve for a clinic's Inbox. */
@RestController
public class InboxController {

    private final InboxItemService inboxItemService;
    private final InboxBroadcastService inboxBroadcastService;

    public InboxController(InboxItemService inboxItemService, InboxBroadcastService inboxBroadcastService) {
        this.inboxItemService = inboxItemService;
        this.inboxBroadcastService = inboxBroadcastService;
    }

    @GetMapping("/api/v1/clinics/{clinicId}/inbox")
    public List<InboxItemResponse> list(@PathVariable UUID clinicId, Authentication authentication) {
        return inboxItemService.list(clinicId, SecurityConfig.currentAccountId(authentication));
    }

    @GetMapping("/api/v1/clinics/{clinicId}/inbox/stream")
    public SseEmitter stream(@PathVariable UUID clinicId, Authentication authentication) {
        inboxItemService.requireAuthorized(SecurityConfig.currentAccountId(authentication), clinicId);
        return inboxBroadcastService.subscribe(clinicId);
    }

    @PostMapping("/api/v1/clinics/{clinicId}/inbox/{itemId}/claim")
    public InboxItemResponse claim(
            @PathVariable UUID clinicId, @PathVariable UUID itemId, Authentication authentication) {
        return inboxItemService.claim(clinicId, itemId, SecurityConfig.currentAccountId(authentication));
    }

    @PostMapping("/api/v1/clinics/{clinicId}/inbox/{itemId}/release")
    public InboxItemResponse release(
            @PathVariable UUID clinicId, @PathVariable UUID itemId, Authentication authentication) {
        return inboxItemService.release(clinicId, itemId, SecurityConfig.currentAccountId(authentication));
    }

    @PostMapping("/api/v1/clinics/{clinicId}/inbox/{itemId}/resolve")
    public InboxItemResponse resolve(
            @PathVariable UUID clinicId, @PathVariable UUID itemId, Authentication authentication) {
        return inboxItemService.resolve(clinicId, itemId, SecurityConfig.currentAccountId(authentication));
    }
}
