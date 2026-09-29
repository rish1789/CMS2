package com.cms.scheduling.api;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.service.SlotAppearedService;

import com.cms.identity.account.config.SecurityConfig;
import com.cms.scheduling.dto.SlotAppearedResponse;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SlotAppearedController {

    private final SlotAppearedService slotAppearedService;

    public SlotAppearedController(SlotAppearedService slotAppearedService) {
        this.slotAppearedService = slotAppearedService;
    }

    @PostMapping("/api/v1/clinics/{clinicId}/slots/{slotId}/appeared")
    public SlotAppearedResponse markAppeared(
            @PathVariable UUID clinicId, @PathVariable UUID slotId, Authentication authentication) {
        UUID callerAccountId = SecurityConfig.currentAccountId(authentication);
        Slot slot = slotAppearedService.markAppeared(callerAccountId, clinicId, slotId);
        return SlotAppearedResponse.of(slot);
    }
}
