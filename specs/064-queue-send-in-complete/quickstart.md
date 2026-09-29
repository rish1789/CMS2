# Quickstart: Send-In and Complete for Queue Sessions (064)

Live check on the dev stack with a throwaway clinic that has a Queue schedule today and a fee and appointment type set up.

1. **Booking marks waiting (FR-001)**: book 4 tokens (2 patient self-service, 1 staff, 1 front-desk walk-in) → all 4 tokens are `BOOKED`.
2. **Positions (FR-004)**: tokens 1–4 show positions 1–4, in both the patient and staff views.
3. **Send in (FR-002)**: send in token 1 → `APPEARED`, `appeared_at` set; token 4 now shows 3; token 1 shows no position (FR-005).
4. **Complete (FR-003)**: complete token 1 → `COMPLETED`, `completed_at` set.
5. **Cancel (FR-009)**: staff cancel token 3 → booking CANCELLED, token `OPEN`, no waitlist offer; token 4 now shows 1.
6. **Patient self-cancel unchanged**: still 409 `NOT_A_FIXED_TIME_SESSION` for a queue booking.
7. **Front desk (FR-007)**: the Walk-in screen lists the queue session with a waiting count and the free/busy hint; the side panel shows its waiting tokens with Send in / Complete / Remove.
8. **Migration (FR-011)**: the 2 pre-existing Star Clinic active queue bookings now have `BOOKED` tokens.
9. **Sweeps (FR-010)**: waiting and in-with-doctor tokens are never auto-marked no-show or auto-completed.
