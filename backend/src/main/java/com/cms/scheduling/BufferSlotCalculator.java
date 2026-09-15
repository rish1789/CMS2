package com.cms.scheduling;

/**
 * 018 FR-004: the buffer-slot-count seam - 022-buffer-slot-capacity-sizing (not yet
 * built) will supply a different implementation using trailing no-show history, with
 * zero changes to {@link SlotGenerationService}'s slot-creation or even-distribution
 * logic (build-order.md's own documented "upgrade in place" intent).
 */
public interface BufferSlotCalculator {

    int calculateBufferSlotCount(Session session);
}
