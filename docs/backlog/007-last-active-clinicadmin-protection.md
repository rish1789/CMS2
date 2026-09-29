# 007 — Last Active ClinicAdmin Protection

**Module:** Identity & Access
**Status:** Ready for spec-kit intake

## User Story
As the system, I want to prevent a clinic's last active ClinicAdmin from ever being deactivated or removed, so that a clinic can never end up without any administrator able to manage it.

## Context
BDD §3.1: "A clinic's last active ClinicAdmin can never be deactivated or removed." This is the ongoing-lifecycle counterpart to the atomicity rule in 001-clinic-registration (a clinic can never exist without an admin at creation time) — this feature enforces the same invariant continuously, not just at creation.

## Business Rules
- Before any deactivation or removal action targeting a ClinicAdmin's Role Assignment, the system must check whether this is the clinic's last active ClinicAdmin.
- If it is the last active ClinicAdmin, the action is blocked with a clear, specific error message — no override exists for any role, including Super Admin (the source doc grants no exception).
- "Active" means the Role Assignment has not already been deactivated. If a clinic somehow has more than one active ClinicAdmin, removing any one of them is permitted as long as at least one remains active afterward.
- This check must run at the API/data layer, not only the UI, so it cannot be bypassed.

## Acceptance Criteria
- Given a clinic with exactly one active ClinicAdmin, when anyone attempts to deactivate or remove that ClinicAdmin's role, then the action is rejected with an explanatory error.
- Given a clinic with two active ClinicAdmins, when one is deactivated, then the action succeeds and the other remains active.
- Given the last remaining active ClinicAdmin, when a Super Admin attempts removal, then it is still blocked — Super Admin has no override.

## Dependencies
- Depends on: 001-clinic-registration — establishes the same invariant at creation time; this feature maintains it afterward.
- Related: 004-staff-onboarding-direct-hire — the direct-hire onboarding flow explicitly cannot create a peer ClinicAdmin (only Doctor/Operations), and no other flow in v1 scope creates one either. **Confirmed intended, not a gap**: since a clinic can never gain a second ClinicAdmin, the founding ClinicAdmin's role assignment is effectively permanent — it can never be deactivated or removed by anyone, including Super Admin, for the life of the clinic. If a clinic's founding admin leaves the business, that is handled entirely outside the system; no in-product succession/reassignment path exists in v1.

## Explicitly Out of Scope
- Any override mechanism, for any role, to force-remove the last active ClinicAdmin.

## Source References
- BDD §3.1 (last active ClinicAdmin protection)
- BDD §6.1 (onboarding flow confirms no peer-ClinicAdmin creation path)
