# Data Model: Staff Login (Password or Staff Code)

## No new entity, no schema change

This feature reads the existing `Account.email` and `Account.staffCode` fields (001/004) as two alternate lookup keys resolving to the same row. No new field, table, or migration.

## Query Shape

`AccountRepository.findByStaffCode(String staffCode)` — new method, mirroring the existing `findByEmail`. Both are globally unique lookups (DB constraints from 001), so either returns at most one row.

## Out of Scope for This Data Model

- Any new identifier type beyond email/staff-code.
