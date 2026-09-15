package com.cms.booking;

import java.util.UUID;

/**
 * patient-slot-booking-date-logic: defense in depth for {@link PatientBookingService#bookSlot} -
 * the listing endpoint already floors every Fixed-Time Slot it offers to today-or-later, but a
 * stale client (a slot fetched before midnight, or a direct API call) could still try to book a
 * Slot whose Session has since passed. Distinct from {@link SlotAlreadyBookedException}: the
 * Slot's own {@code status} is still OPEN here - nobody ever booked it, its day simply passed.
 */
public class SlotDateInThePastException extends RuntimeException {

    public SlotDateInThePastException(UUID slotId) {
        super("Slot " + slotId + " is dated in the past and can no longer be booked");
    }
}
