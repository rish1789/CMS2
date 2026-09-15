# Critical Faults — Detailed Look

This document covers the handful of findings serious enough to actually block or confuse a real user,
explained without code. For the complete list of smaller issues, see the executive summary and the
technical companion documents.

---

## Fault #1: A patient trying to book their own appointment may be told a free slot is "already booked"

**The plumbing analogy:** Imagine a clinic has two separate ID-card systems — a staff badge system, and a
separate patient membership-card system, deliberately kept apart so a lost patient card can never be used
to badge into a staff area, and vice versa. Now imagine the appointment-booking log book has a single
column labeled "staff badge number of whoever wrote this entry" — and when a *patient* books their own
appointment online, the system tries to write their *membership card number* into that "staff badge
number" column. The log book's own rules say that column must contain a real, valid staff badge number.
A patient's membership number is not one. The entry gets rejected.

**What the user would actually see:** Rather than a clear "something went wrong with our system," a
patient attempting to book an available appointment slot would very likely see a message like "this slot
is already booked" — even though the slot is genuinely free. This is because the specific way this
rejection happens gets misinterpreted, elsewhere in the code, as if someone else had just grabbed the
slot first.

**Who this affects:** Every patient using the self-service booking feature — both the "pick a specific
time" booking flow and the "take a queue number" booking flow. Staff-assisted bookings (where a front-desk
person books on the patient's behalf) are not affected — that path uses the staff badge system correctly.

**How confident are we:** Very. We traced this through three independent pieces of evidence that all
agree: the database's own rulebook, the actual code that writes the booking record, and every place in
the code that constructs one. We were not able to actually run the booking flow against a real database
in this environment to watch it fail with our own eyes — the test database simulator used for this kind
of verification has a known, pre-existing issue in this development environment that predates and is
unrelated to this scan — but the written evidence lines up too precisely, in too many places, to be a
false alarm.

**Priority:** Fix and verify before this system is used for real patient self-service bookings. This is
the one issue in this entire report that could plausibly stop a real customer from doing the thing they
came to do.

---

## Fault #2: Staff members hit dead ends with zero explanation when their login session expires

**The plumbing analogy:** Picture a keycard door that, when your card has expired, just... does nothing.
No red light, no beep, no "please see reception" sign — the door simply doesn't open, and you're left
standing there with no idea whether you pushed wrong, whether the door is broken, or whether your card
expired. You'd probably try again a few times before giving up.

**What the user would actually see:** A staff member's login naturally expires after several hours (this
is normal, expected security behavior, not a bug). If that happens while they're in the middle of, for
example, booking an appointment, marking an appointment slot as completed, or anonymizing a patient
record for privacy-law compliance — the button they click simply resets itself with **no error message
at all**. Nothing pops up telling them to log back in. It looks exactly like the click "did nothing,"
which is a uniquely frustrating and confusing failure mode compared to literally any visible error
message.

**Who this affects:** Staff using several of the most commonly-used day-to-day screens, specifically
whenever their session has quietly timed out mid-task.

**Why it's not worse than it sounds:** No data is corrupted or lost when this happens — the action simply
never went through, silently, rather than failing loudly. It's a wasted-time-and-trust problem, not a
data-safety problem.

**Priority:** High — this is one of the cheapest fixes in the whole report (a small, consistent code
change per affected screen) for one of the most disproportionately frustrating user experiences it would
produce in daily use.

---

## Fault #3: A few error messages point to the wrong part of the app entirely

**The plumbing analogy:** You call the plumber about a leaking sink, and the automated message system
plays you a recording meant for the electrician. The information itself might even technically be
accurate in some generic sense ("someone is not authorized to do this"), but it names the wrong
department, and it undermines confidence that the system understands what's actually happening.

**What the user would actually see:** On a handful of screens — writing a doctor's consultation note,
adding a prescription, and anonymizing a patient's record for privacy compliance — if a staff member
tries an action they don't have permission for, the error message they see literally says something
about "managing schedules," which has nothing to do with any of those three screens. This happened
because a piece of error-message text written for one specific, unrelated feature got reused elsewhere
in the code without being reworded for its new context.

**Who this affects:** Staff members who trigger a permission error on those specific screens — likely a
small fraction of overall usage, but a confusing and unprofessional-looking moment whenever it happens.

**Priority:** Medium — doesn't block anyone from doing their job (the action is still correctly denied),
but it's a quick, worthwhile polish fix that improves how trustworthy the application feels.

---

## Everything else

The remaining findings in the technical companion documents are lower-severity: a few more screens with
the same "silent failure on an expired login" pattern as Fault #2, a couple of built-but-unreachable
screens (a patient can't yet see or act on a waitlist offer notification because no screen shows it to
them yet, and staff have no screen yet to configure appointment types/fees, so a couple of booking forms
require someone to already know an internal ID number by heart), and a handful of small, low-traffic
input-validation and messaging inconsistencies. None of these lose or corrupt data; all of them are
documented individually, with exact fix instructions, in `../claude_context/`.
