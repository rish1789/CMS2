# Feature Specification: Super Admin RBAC Login & Console Access

**Feature Branch**: `040-super-admin-rbac-login`

**Created**: 2026-09-08

**Status**: Draft

**Input**: User description: "Implement a Role-Based Access Control (RBAC) login flow for a Super Admin. 1. Unified Login Hook: Modify the existing login logic on the staff and patient portals. 2. Role Detection & Redirection: Upon successful authentication, inspect the user's role/token. If their role is SUPER_ADMIN, redirect them immediately to /super-admin-console instead of the default staff or patient dashboard. 3. Frontend Route Protection: Create or update the frontend auth guard/middleware. The /super-admin-console route must strictly check for the SUPER_ADMIN role and kick unauthorized users back to the login page. 4. Backend Endpoint Security: Create or update the backend authorization middleware. Ensure that any endpoints serving the super admin console (e.g., /api/super-admin/*) explicitly reject requests that do not have the super admin role in their verified session or JWT."

## Codebase Context *(carried forward from pre-specify investigation)*

This system already has three **entirely separate** identity/authentication mechanisms, with no shared login endpoint or shared session format:

- **Staff** (`002-super-admin-clinic-verification`, `004-staff-onboarding-direct-hire`, `003-staff-login-password-or-code`): DB-backed `Account` + `RoleAssignment`, login at `POST /api/v1/staff/login`, issues a bearer JWT (audience `staff`) stored client-side in `sessionStorage`. The JWT carries only the account ID — no role claim. Role checks (ClinicAdmin/Doctor/Operations) happen server-side per request via `RoleAssignmentRepository` lookups, not by reading the token.
- **Patient** (`039-patient-account-global-login`): DB-backed `PatientAccount`, its own login endpoint, its own bearer JWT (audience `PATIENT`), same "no role claim in token" shape.
- **Super Admin** (`002-super-admin-clinic-verification`): **no DB-backed identity at all** — a single config-bootstrapped in-memory credential pair, authenticated via **HTTP Basic Auth re-sent on every request** to `/api/v1/admin/**`. There is no login endpoint, no issued token, no session of any kind, and no frontend login page — today, each Super Admin screen (e.g. `PendingClinicsList`) collects a username/password into its own local component state and attaches it as a Basic Auth header on each API call it makes.

This directly conflicts with the feature request's literal framing ("inspect the user's role/token", "verified session or JWT", "unified login hook covering staff and patient portals"), which assumes a session/token-based model uniform across all three identities. That conflict is the subject of the clarification questions below — this spec proceeds once it is resolved.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Super Admin signs in through the Clinic Portal and lands on the console (Priority: P1)

A Super Admin opens the **Clinic Portal** login screen — the same screen ClinicAdmins, Doctors, and Operations staff use — enters their Super Admin credentials, and is taken straight to the Super Admin console, without ever seeing a staff dashboard first.

**Why this priority**: This is the entire point of the feature. Without it, there is no Super Admin login flow to protect.

**Independent Test**: Enter valid Super Admin credentials at the Clinic Portal login screen and confirm the browser lands on `/super-admin-console` with the console's content visible.

**Acceptance Scenarios**:

1. **Given** a person with valid Super Admin credentials, **When** they submit those credentials at the Clinic Portal login screen, **Then** the system recognizes them as Super Admin (not staff) and redirects them to `/super-admin-console`, usable immediately.
2. **Given** a person with invalid Super Admin credentials, **When** they submit them, **Then** they see an authentication error and remain on the login screen.

---

### User Story 2 - Staff logins on the shared Clinic Portal, and patient logins on their own portal, are unaffected (Priority: P2)

Clinic staff (ClinicAdmin, Doctor, Operations) log in on the same Clinic Portal screen as Super Admin, using their existing credentials, and land on their existing staff dashboard exactly as before — the only change is that the screen is now shared, not that their own login outcome changes. Patients continue to use their own, entirely separate login screen and land on their existing patient dashboard, completely untouched by this feature.

**Why this priority**: This feature touches the shared login code path for staff (by merging its screen with Super Admin's) and the shared redirect logic for all three identities; a regression here would break every other converged feature that depends on staff/patient login.

**Independent Test**: Log in as a ClinicAdmin/Doctor/Operations staff account on the Clinic Portal and confirm the same post-login destination as before this feature; separately, log in as a patient on the Patient Portal and confirm nothing changed.

**Acceptance Scenarios**:

1. **Given** a staff account with no Super Admin role, **When** they log in successfully on the Clinic Portal, **Then** the system recognizes them as staff (not Super Admin) and lands them on the existing staff dashboard, unchanged from current behavior.
2. **Given** a patient account, **When** they log in successfully on the separate Patient Portal, **Then** they land on the existing patient dashboard, unchanged from current behavior.

---

### User Story 3 - The Super Admin console rejects anyone who isn't a signed-in Super Admin (Priority: P1)

Anyone who is not currently authenticated as Super Admin — a logged-out visitor, a staff member, a patient, or a Super Admin whose session has ended — is sent back to the login screen the moment they try to view `/super-admin-console` or call any endpoint behind it, both in the browser and at the API.

**Why this priority**: This is the actual security boundary the feature exists to enforce; without it, the redirect in User Story 1 is cosmetic only.

**Independent Test**: While logged out, navigate directly to `/super-admin-console` and confirm redirection to the login screen. Separately, call a Super Admin API endpoint with no credentials, with a staff JWT, and with a patient JWT, and confirm each is rejected.

**Acceptance Scenarios**:

1. **Given** no active Super Admin session, **When** a browser navigates directly to `/super-admin-console`, **Then** the app redirects to the login screen instead of rendering the console.
2. **Given** a valid staff or patient session but no Super Admin session, **When** that user navigates to `/super-admin-console`, **Then** the app redirects to the login screen (never rendering console content, even briefly).
3. **Given** a request to a Super Admin API endpoint carrying no credentials, an invalid credential, or a valid staff/patient credential instead of a Super Admin one, **When** the backend receives it, **Then** it is rejected with an authentication/authorization error and no Super Admin data or action is performed.
4. **Given** a Super Admin session that has expired or been ended, **When** the browser is still on `/super-admin-console` and a further action is taken, **Then** the user is returned to the login screen.

### Edge Cases

- A Super Admin bookmarks `/super-admin-console` and opens it directly (not via login) with no active session — must land on the login screen, not a broken or partially-rendered console.
- A staff member manually edits the browser URL to `/super-admin-console` while logged in as staff — must be redirected, never shown even a flash of console content.
- Super Admin credentials are correct but the account has been left unconfigured (no username/password ever set) — login must fail cleanly with an authentication error, not a server error.
- A backend request reaches a Super Admin endpoint with a well-formed but wrong-role credential (e.g., a syntactically valid staff JWT) — must be rejected the same way as a missing credential, not treated as "authenticated but under-permissioned" in a way that leaks endpoint existence/behavior.
- The Clinic Portal login screen and the Patient Portal login screen must each independently handle "already logged in, navigate to login again" without crashing or looping.
- Someone submits credentials on the Clinic Portal that match neither the configured Super Admin credential nor any staff Account — must fail with one generic authentication error, without revealing which of the two checks it failed (no "no such staff account" vs. "wrong admin password" distinction visible to the caller).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide one combined **Clinic Portal** login screen, shared by Super Admin, ClinicAdmin, Doctor, and Operations staff, replacing Super Admin's current "supply credentials inline on each individual admin action" pattern with a real, one-time login step.
- **FR-002**: The Patient login screen and endpoint MUST remain entirely separate from the Clinic Portal — this feature MUST NOT merge patient authentication into the combined login.
- **FR-003**: When credentials are submitted at the Clinic Portal, the system MUST determine whether they belong to the configured Super Admin or to a staff Account, and authenticate against whichever identity actually matches — without requiring the caller to declare in advance which one they are.
- **FR-004**: If the Clinic Portal credentials match neither the Super Admin nor any staff Account, the system MUST reject the attempt with one generic authentication error that does not reveal which check failed.
- **FR-005**: Upon successful Clinic Portal authentication resolved as Super Admin, the system MUST send the user directly to the Super Admin console, without passing through the staff dashboard.
- **FR-006**: Upon successful Clinic Portal authentication resolved as staff, the system MUST continue to send the user to the existing staff dashboard, exactly as it does today.
- **FR-007**: Upon successful Patient Portal authentication, the system MUST continue to send the user to the existing patient dashboard, exactly as it does today (fully unaffected by this feature).
- **FR-008**: The frontend MUST prevent the Super Admin console from rendering for anyone without an active, valid Super Admin session — including logged-out visitors, staff, and patients — redirecting them to the Clinic Portal login screen instead.
- **FR-009**: The backend MUST reject any request to a Super Admin-only endpoint that does not carry a valid, currently-verified Super Admin credential, regardless of whether the caller is unauthenticated or authenticated as a different identity (staff or patient).
- **FR-010**: A rejected Super Admin console access attempt (frontend) or Super Admin endpoint call (backend) MUST NOT reveal Super Admin data or perform any Super Admin action.
- **FR-011**: The post-authentication role-detection-and-redirect behavior (Super Admin → console, staff → staff dashboard) MUST be applied via one shared decision point on the Clinic Portal, rather than duplicated logic; the Patient Portal's own redirect is unaffected and separate (per FR-002/FR-007).
- **FR-012**: Super Admin authentication MUST be backed by a real login token (JWT) issued at successful login and used for every subsequent Super Admin API call, replacing the current per-request HTTP Basic Auth mechanism. The token MUST carry a distinguishing claim (mirroring the existing staff `staff` and patient `PATIENT` JWT audiences) that identifies it as a Super Admin token, distinct from a staff token even though both now originate from the same login screen.

### Key Entities *(include if feature involves data)*

- **Super Admin Session** *(new concept)*: Represents "this browser tab is currently authenticated as Super Admin," created by a successful Clinic Portal login resolved as Super Admin (FR-003, FR-012) and consulted by the frontend route guard. Backed by a JWT, mirroring the existing staff/patient session shape, not a stored raw credential.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of successful Super Admin logins (via the Clinic Portal) land on the Super Admin console, with 0% landing on a staff dashboard first.
- **SC-002**: 100% of direct-URL or bookmarked navigations to the Super Admin console by anyone without an active Super Admin session are redirected to the login screen, with 0% of console content ever visible to them.
- **SC-003**: 100% of Super Admin API requests lacking a valid, currently-verified Super Admin credential are rejected, including requests carrying an otherwise-valid staff or patient credential.
- **SC-004**: Existing staff login success rates and post-login destinations are unchanged after this feature ships, aside from now sharing a login screen with Super Admin (0 regressions in outcome). Patient login is entirely unaffected (0 changes of any kind).

## Assumptions

- The Super Admin console's frontend routes live under a single new path prefix, `/super-admin-console`, distinct from the existing `/admin/*` routes (`AdminShell`, `PendingClinicsList`, etc.) referenced in prior features; this feature does not rename or move those existing screens, but makes them reachable only under the new guarded prefix (or wraps them with the new guard in place — an implementation-planning decision, not a scope question).
- The backend's existing `/api/v1/admin/**` path prefix (established by `002-super-admin-clinic-verification`) is the "Super Admin console" endpoint family this feature secures — the user's illustrative `/api/super-admin/*` is treated as the same thing under the project's actual `/api/v1/...` convention, not a second, additional prefix to create.
- No new Super Admin identity/credential store is introduced — this feature secures access to the console, it does not change how many Super Admins exist or how their credentials are provisioned (still config-bootstrapped, per `002`); there being exactly one configured Super Admin credential (not a table of many) is what makes the "try Super Admin, then fall back to staff" resolution in FR-003 cheap and unambiguous.
- The Clinic Portal is a new combined login screen; on the backend it extends the existing staff login endpoint (`POST /api/v1/staff/login`) to also try the Super Admin credential, rather than introducing a second endpoint — it does not replace or remove that endpoint's existing staff verification logic, it fronts both verification paths through the same one screen and one entry point.
- "Kick unauthorized users back to the login page" means the Clinic Portal login screen specifically (not the Patient Portal's), since that is the credential a Super Admin console visitor needs.
- Logout/session-expiry UX (e.g., an explicit "log out" button) is not specified by the source request and is out of scope beyond what FR-008/US3's expired-session edge case requires structurally.
- Existing Super Admin-authenticated frontend screens that currently hold their own local username/password fields and attach Basic Auth per call (e.g. `PendingClinicsList`, `PendingDoctorsList`, `TriggerSessionGeneration`) are updated to use the new stored JWT session instead of their own inline credential forms — this is necessarily in scope since FR-012 removes Basic Auth as the verification mechanism those screens currently depend on.
- The Clinic Portal reuses the existing staff login's single "identifier + password" field shape (already dual-purpose for email-or-staff-code per `003-staff-login-password-or-code`) rather than introducing a separate visible "Super Admin" mode/tab — the Super Admin's configured username is simply a third value that identifier field can match, keeping one visual form for all Clinic Portal callers.
- No new rate-limiting/account-lockout mechanism is introduced for the Clinic Portal, consistent with every existing login endpoint in this codebase (staff, patient) having none today; this is a pre-existing, accepted risk posture this feature does not change.
- The new Super Admin JWT uses the same token lifetime as the existing staff JWT (12 hours), with no additional expiry restriction — no stated requirement calls for a shorter Super Admin session.
