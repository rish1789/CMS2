# Contract Delta: `PatientLinkingService.findOrCreatePatient` (066)

The base contract is `specs/009-patient-record-phone-linking/contracts/patient-linking-service.md`. It is **unchanged in signature and outcomes**. This file records only what 066 makes true and newly guarantees.

```java
Patient findOrCreatePatient(UUID patientAccountId, UUID clinicId, String name)
```

## Unchanged

- Inputs, return value and exceptions (`PatientAccountNotFoundException`, unknown clinic).
- The resolution order: existing link (FR-002), then phone match (FR-003), then new record (FR-004).
- Transaction participation: the method joins the caller's transaction when there is one, and otherwise runs in its own.

## Now actually honoured (it was specified in 009 but broken)

- **Both succeed.** Two or more concurrent calls with the same `(patientAccountId, clinicId)` and no pre-existing record all return normally, all with the same `Patient` id. No exception reaches any caller because of the race (009 FR-006, SC-004; 066 FR-001).

## New guarantees (066)

- **Winner rollback.** If the concurrent call that would create the record ends in rollback, a waiting call still returns normally, creating the record itself (066 FR-004).
- **Serialization.** Concurrent calls for the same Patient Account are serialized for the duration of each caller's transaction. Calls for *different* accounts are not affected (research.md R3).
- **Other conflicts still fail.** A `uq_patient_clinic_account` violation raised by any other writer, or any other data error, propagates to the caller unchanged. The method no longer attempts an in-transaction re-read (066 FR-006, research.md R1).
