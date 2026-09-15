CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE clinic (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name           VARCHAR(255) NOT NULL,
    address        VARCHAR(500) NOT NULL,
    contact_email  VARCHAR(255),
    contact_mobile VARCHAR(20),
    verified       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    staff_code    VARCHAR(20) NOT NULL,
    mobile        VARCHAR(20),
    active        BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_account_email UNIQUE (email),
    CONSTRAINT uq_account_staff_code UNIQUE (staff_code)
);

CREATE TABLE role_assignment (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES account (id),
    clinic_id  UUID NOT NULL REFERENCES clinic (id),
    role       VARCHAR(20) NOT NULL CHECK (role IN ('ClinicAdmin', 'Doctor', 'Operations')),
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_role_assignment_account_id ON role_assignment (account_id);
CREATE INDEX idx_role_assignment_clinic_id ON role_assignment (clinic_id);
