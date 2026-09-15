ALTER TABLE waitlist_entry ADD COLUMN offered_slot_id UUID REFERENCES slot (id);
