package com.cms.scheduling;

import org.springframework.stereotype.Component;

/**
 * 018: v1's only {@link BufferSlotCalculator} - a literal transcription of
 * 022-buffer-slot-capacity-sizing's own already-specified "fewer than 5 no-show data
 * points -> exactly 1 buffer slot" cold-start rule, which is unconditionally true right
 * now since no no-show data exists anywhere in this codebase yet (021 is unbuilt too).
 * Not a guess - see spec.md Scope Decisions and research.md.
 */
@Component
public class ColdStartBufferSlotCalculator implements BufferSlotCalculator {

    @Override
    public int calculateBufferSlotCount(Session session) {
        return 1;
    }
}
