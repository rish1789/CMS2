# Executive Summary — CMS2 Frontend/Backend Diagnostic Scan

## The short version

The backend (the "kitchen") is very well built. The frontend screens (the "dining room") are, for the
most part, individually well made too. But **the two have never been connected into one working
restaurant** — there's no host stand seating anyone at a table, and a few of the kitchen's pipes are
plumbed into the wrong drain. Here's what that means in practice, and what to do about it.

## The single biggest problem: there's no front door

Right now, if you started this application up, the *only* screen a visitor could ever reach is the
clinic-registration form. Every other screen — staff login, booking an appointment, viewing the waitlist,
the doctor's inbox, cancelling a session, all 22 of them — exists, has been built, and has been tested in
isolation, but there is no menu, no navigation, no "app" tying them together. It's as if a construction
crew built 23 fully-furnished rooms in a house, wired and plumbed each one correctly, but never built the
hallways connecting them to the front door. Nobody outside the crew can actually walk from one room to
another.

This is the root cause behind almost every "nobody can use this feature" finding in this report. It is
not a sign of sloppy work — each room, individually, was built to a genuinely high standard — but it does
mean the system as a whole is not yet a usable product, only a well-stocked set of parts.

## The second biggest problem: a few pipes are hooked up to the wrong system

Deeper in the plumbing, we found one specific, serious cross-wiring: this clinic system actually has
**two separate identity systems** — one for staff (doctors, front-desk operations, admins) and one for
patients who create their own login. That separation is intentional and correct, by design, everywhere
else in the system. But the pipe that records "who made this booking" was built assuming only staff would
ever use it. When a patient books an appointment for themselves through the self-service booking flow,
the system tries to write the patient's ID into a slot that was only ever plumbed to accept a staff ID —
and on a real, live database, that connection would fail.

The user-facing symptom would be confusing rather than a crash: a patient trying to book a genuinely open
appointment slot would very likely see "this slot is already booked" — even though it isn't. We found
this by tracing the pipe from end to end (the database's own rulebook, the code that writes to it, and
every place that writes) — we have not yet been able to turn the water on and watch it actually fail,
because this development environment's own database simulator has never been able to start (a known,
pre-existing limitation, not something new we broke). But the trace is clean and consistent across three
independent pieces of evidence, so we're treating it as a near-certain, high-priority fix rather than a
maybe.

## The third pattern: error messages that either say nothing, or say the wrong thing

Across a large number of screens, when something goes wrong, one of two things happens instead of a
helpful message:

1. **Total silence.** The button just goes back to normal, as if nothing happened, with zero explanation.
   This happens most often when a staff member's login session has quietly expired — a routine,
   everyday occurrence, not a rare edge case — and it happens on several of the most commonly-used
   screens (booking an appointment, anonymizing a patient record, completing an appointment slot). A
   staff member in this situation would likely think the app is broken or frozen, try clicking again a
   few times, and eventually give up or call for help, when the actual fix is simply "log in again."

2. **The wrong, overly technical message.** On several screens (writing a consultation note, adding a
   prescription, anonymizing a patient), if a staff member tries an action they're not allowed to do, the
   message they see literally refers to "schedules" — a completely unrelated part of the app, left over
   from a message that was written for a different screen and accidentally reused. It's a bit like
   pressing "cancel my flight" and getting told "your hotel reservation could not be modified."

Neither of these degrades any actual data — they're user-experience problems, not data-loss problems —
but both would generate real support calls and real staff frustration once this system is used day to
day, and both have a genuinely simple fix once identified (which we've spelled out precisely, screen by
screen, in the technical companion documents).

## What this does *not* affect

It's worth being explicit about the good news, because it's substantial: the actual "wiring diagram"
between the two halves of the system — which piece of data goes where, using what name, in what format —
is correct in the overwhelming majority of places we checked. Every screen's data fields line up with
what the backend expects. Every status label (booked, cancelled, completed, and so on) is spelled
identically on both sides everywhere it's used. The database's own rulebook matches the code almost
perfectly. This is a system that was built carefully and tested rigorously at the level of "does each
individual piece work correctly" — the gaps we found are almost entirely about *connecting* those pieces
together into one coherent, forgiving, usable whole, plus the one plumbing cross-wire described above.

## What we'd recommend doing, in order

1. **Fix the pipe cross-wiring** (patient bookings writing the wrong identity into the "who booked this"
   field) — this is the one issue in the whole report with the potential to actually break a real
   patient's ability to book an appointment, so it should be confirmed and fixed before anything else.
2. **Add the two or three most common "say nothing has failed" fixes** — these are simple, one-line
   changes per screen, and they'd immediately stop the most confusing failure mode (a silently-expired
   login session) from feeling like the app has crashed.
3. **Fix the "wrong department" error messages** — also simple, and immediately improves the credibility
   of the whole application in staff members' eyes.
4. **Build the hallways** — connect the 22 unreachable screens into one navigable application with a real
   front door, login-aware routing, and role-based access, so the very substantial amount of already-
   built, already-tested work becomes something people can actually use.

See `02_critical_faults.md` for a closer look at the single most serious issue, and the technical
companion documents in `../claude_context/` for the complete, itemized list with exact fix instructions
for each issue mentioned above.
