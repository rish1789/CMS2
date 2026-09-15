-- 011-notification-event-pipeline: two additive, independent per-channel opt-in flags on
-- the existing patient_account table (039's pre-existing notification_opt_in column is
-- left untouched - zero consumers today, out of this feature's scope; see research.md),
-- plus the new notification_event table, this feature's own generic pub/sub entity.

ALTER TABLE patient_account
    ADD COLUMN sms_opt_in BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN push_opt_in BOOLEAN NOT NULL DEFAULT true;

CREATE TABLE notification_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_account_id  UUID NOT NULL REFERENCES patient_account (id),
    event_type          VARCHAR(255) NOT NULL,
    payload             TEXT,
    push_eligible       BOOLEAN NOT NULL,
    sms_eligible        BOOLEAN NOT NULL,
    expires_at          TIMESTAMPTZ,
    status              VARCHAR(20) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Backs expireDue()'s bulk-update predicate (status = PENDING AND expires_at < :now).
CREATE INDEX idx_notification_event_status_expires_at
    ON notification_event (status, expires_at);
