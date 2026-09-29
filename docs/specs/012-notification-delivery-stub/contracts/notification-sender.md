# Contract: NotificationSender (service interface, not a REST endpoint)

Per Constitution Principle III's explicit allowance ("a clear contract — service interface or REST endpoint"). This feature has no HTTP endpoint — there is no external caller, only an internal listener. `com.cms.notification.NotificationSender`:

## `send(String channel, String recipient, String message) -> void`

### Parameters

| Name | Required | Notes |
|---|---|---|
| `channel` | yes | `"push"` or `"sms"` in this feature's own usage; the interface itself does not constrain the value — a future implementation may support others |
| `recipient` | yes | The patient's email (push) or mobile number (sms), per `NotificationDeliveryListener`'s call sites |
| `message` | yes | Derived from the triggering `NotificationEvent`'s `eventType`/`payload`; opaque to this interface |

### Return

None. No exception is defined by the contract itself — the v1 implementation (`LoggingNotificationSender`) never throws; a future real implementation's own error-handling policy is that implementation's concern, not this contract's.

## Contract Invariants (traced to spec)

- The v1 implementation (`LoggingNotificationSender`) never makes an outbound network call of any kind (FR-002, SC-002).
- The v1 implementation requires no injected configuration, credential, or external client bean (FR-004, SC-003).
- `NotificationDeliveryListener` (the only caller in this feature) invokes `send` exactly once per eligible channel on a committed `NotificationEvent`, and never for an ineligible channel or an uncommitted one (FR-001, FR-005).
