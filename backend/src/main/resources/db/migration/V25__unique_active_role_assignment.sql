-- Prevents duplicate active RoleAssignments for the same (account, clinic, role),
-- e.g. onboarding the same doctor at the same clinic twice.
CREATE UNIQUE INDEX uq_role_assignment_account_clinic_role_active
    ON role_assignment (account_id, clinic_id, role)
    WHERE active;
