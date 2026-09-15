-- Employee deactivation modal: captures why a staff member was deactivated. Nullable - existing
-- rows (and any pre-this-feature deactivation) have none, and it's only ever set at the moment
-- an active RoleAssignment transitions to inactive.
ALTER TABLE role_assignment
    ADD COLUMN deactivation_reason VARCHAR(30)
    CHECK (deactivation_reason IN ('RESIGNED', 'SERVICE_NOT_REQUIRED'));
