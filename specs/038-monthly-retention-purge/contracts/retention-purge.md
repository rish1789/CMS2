# Contract: Retention Purge Manual Trigger

`POST /api/v1/admin/retention-purge/run`

Sits behind `SuperAdminSecurityConfig`'s existing `/api/v1/admin/**` HTTP Basic Auth chain (`@Order(3)`, single in-memory Super Admin identity). Since that chain's only valid identity is the Super Admin, successful authentication *is* the authorization check for FR-008 — no additional role check is needed in the controller (mirrors `ClinicVerificationController`/`DoctorVerificationController`, neither of which perform an extra role check beyond the security chain itself).

## Request

No path/query params, no body.

```http
POST /api/v1/admin/retention-purge/run
Authorization: Basic <super-admin-credentials>
```

## Responses

### 200 OK — purge ran (whether or not anything was eligible)

```json
{
  "purgedBookingCount": 3
}
```

Runs the identical logic used by the automatic monthly `RetentionPurgeTrigger` (FR-007). Safe to call repeatedly — a booking with nothing left to purge is a no-op (FR-011).

### 401 Unauthorized — missing/invalid Basic Auth credentials

No body beyond Spring Security's default; returned by the security filter chain before the controller is reached. Covers all of: ClinicAdmin/Doctor/Operations staff-JWT holders (wrong auth scheme entirely for this path), and any request with no or wrong Super Admin credentials.

## Automatic trigger (no HTTP contract — internal `@Scheduled` job)

`RetentionPurgeTrigger.runRetentionPurge()` — `com.cms.clinical`, `@Scheduled(cron = "0 0 0 1 * *")` (midnight on the 1st of each month). Calls the same `RetentionPurgeService.purge()` method the manual-trigger controller calls. No externally observable contract beyond its effect on the data (spec.md Acceptance Scenarios 1-5).
