-- 028-individual-booking-cancellation: a cancelled Booking is retained (not deleted), so the
-- flat one-Booking-ever-per-Slot constraint from V12 (016) must become scoped to non-cancelled
-- Bookings only - allowing a Slot to be booked again by a new Booking after its prior one is
-- cancelled, while still preventing two simultaneously-active Bookings on the same Slot.
ALTER TABLE booking ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';

DROP INDEX uq_booking_slot;

CREATE UNIQUE INDEX uq_booking_slot_active ON booking (slot_id) WHERE status <> 'CANCELLED';
