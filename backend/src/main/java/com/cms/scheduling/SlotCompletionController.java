package com.cms.scheduling;

import com.cms.identity.account.SecurityConfig;
import com.cms.scheduling.dto.SlotCompletionResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotCompletionController {

    private final SlotCompletionService slotCompletionService;

    public SlotCompletionController(SlotCompletionService slotCompletionService) {
        this.slotCompletionService = slotCompletionService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/slots/{slotId}/complete")
    public SlotCompletionResponse complete(
            @PathVariable UUID clinicId, @PathVariable UUID slotId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Slot slot = slotCompletionService.completeSlot(callerAccountId, clinicId, slotId);
        return SlotCompletionResponse.of(slot);
    }
}
