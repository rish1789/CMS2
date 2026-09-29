package com.cms.scheduling.dto;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.util.UUID;

public record SlotCompletionResponse(UUID slotId, SlotStatus status) {

    public static SlotCompletionResponse of(Slot slot) {
        return new SlotCompletionResponse(slot.getId(), slot.getStatus());
    }
}
