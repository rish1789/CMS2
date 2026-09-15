package com.cms.booking;

/** 025 FR-003: a priority-(3) insertion (no buffer or no-show-freed Slot available) was attempted without a non-blank override reason. */
public class OverrideReasonRequiredException extends RuntimeException {

    public OverrideReasonRequiredException() {
        super("An override reason is required to insert a walk-in into a regular slot");
    }
}
