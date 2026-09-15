CREATE TABLE inbox_item (
    id UUID PRIMARY KEY,
    clinic_id UUID NOT NULL REFERENCES clinic (id),
    item_type VARCHAR(32) NOT NULL,
    booking_id UUID REFERENCES booking (id) ON DELETE CASCADE,
    waitlist_entry_id UUID REFERENCES waitlist_entry (id) ON DELETE CASCADE,
    doctor_name VARCHAR(255),
    cancelled_booking_count INTEGER,
    status VARCHAR(16) NOT NULL DEFAULT 'UNCLAIMED',
    claimed_by_account_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_inbox_item_clinic_status ON inbox_item (clinic_id, status);
