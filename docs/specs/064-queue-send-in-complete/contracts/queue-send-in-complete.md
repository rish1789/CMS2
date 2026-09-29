# Contract: Send-In and Complete for Queue Sessions (064)

No new endpoints. Existing endpoints change behavior as follows.

| Endpoint | Before | After |
|---|---|---|
| `POST /clinics/{c}/slots/{slotId}/appeared` | 409 `NOT_A_FIXED_TIME_SESSION` for a queue token | 200 for a waiting queue token; stamps `appearedAt` |
| `POST /clinics/{c}/slots/{slotId}/complete` | 409 `NOT_A_FIXED_TIME_SESSION` for a queue token | 200 for a queue token (staff: waiting or in with doctor; treating doctor: in with doctor); stamps `completedAt` |
| `POST /clinics/{c}/bookings/{bookingId}/cancel` (staff) and batch cancel | 409 `NOT_A_FIXED_TIME_SESSION` for a queue booking | 200; token freed (`OPEN`), no waitlist offer |
| `POST /patients/bookings/{id}/cancel` (patient) | 409 `NOT_A_FIXED_TIME_SESSION` for a queue booking | unchanged |
| `GET` queue position (patient and staff) | always 1 in the real flow | waiting tokens ahead + 1; `applicable: false` once the own token is not waiting |
| `GET /clinics/{c}/sessions` | Queue: `walkInsWaiting = 0`, `inWithDoctor = false` | Queue: `walkInsWaiting` = waiting tokens (booked or walk-in); `inWithDoctor` = any token in with the doctor |
| Booking a queue token (patient, staff, front desk) | token `OPEN` | token `BOOKED` |
