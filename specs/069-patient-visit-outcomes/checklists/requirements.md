# Requirements checklist: 069

- [x] Every audit finding (1–3) maps to an FR: finding 1 → FR-004, finding 2 → FR-001 and FR-006, finding 3 → FR-002, FR-003, FR-005 and FR-007.
- [x] The decision table covers every booking × slot × date combination.
- [x] The only previously undefined behaviour (delayed and queue next-visit) is resolved explicitly.
- [x] No change to cancellation policy, slot or booking records, or schema.
- [x] Ownership is required on every new or changed read.
- [x] The success criteria are testable without real patient data.
