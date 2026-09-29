package com.cms.scheduling.dto;

import com.cms.scheduling.domain.Slot;
import com.cms.scheduling.domain.SlotStatus;
import java.util.UUID;

public record SlotAppearedResponse(UUID slotId, SlotStatus status) {

    public static SlotAppearedResponse of(Slot slot) {
        return new SlotAppearedResponse(slot.getId(), slot.getStatus());
    }
}
