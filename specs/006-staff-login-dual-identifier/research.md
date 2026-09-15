# Research: Staff Login (Password or Staff Code)

Minimal — this is a narrow extension of 004's already-built email+password login. No new stack decisions.

## Decisions

### Identifier resolution

- **Decision**: `StaffLoginRequest.email` is renamed/generalized to `identifier`; `StaffAuthController` tries `AccountRepository.findByEmail(identifier)` first, then `findByStaffCode(identifier)` (a new repository method — `existsByStaffCode` already exists from 004, but no `findBy` variant yet) if the first lookup misses. Either match proceeds identically from that point (password check, JWT issuance).
- **Rationale**: Both email and staff-code are already globally unique (001/004's DB constraints), so trying both lookups is safe and unambiguous — no format-sniffing needed, though email/staff-code formats are structurally distinct anyway (spec.md's Edge Cases).
- **Alternatives considered**: A separate `POST /api/v1/staff/login/by-code` endpoint — rejected; unnecessary API surface for what's the same operation with a different lookup key, and would need its own client-side branching for no benefit.
