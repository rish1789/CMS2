# Product

## Register

product

## Users

Three distinct roles sharing one system, all primarily on desktop/laptop:

- **Patients** — book appointments, join queues/waitlists, check booking status. May be older or less tech-savvy; often anxious or stressed (healthcare context), so flows need to be forgiving and legible, not just functional.
- **Clinic staff** (front desk, operations, doctors) — run day-to-day clinic operations: onboarding, scheduling, bookings/walk-ins, queue management, cancellations, consultation notes/prescriptions, inbox triage. Working fast, often mid-task or mid-conversation with a patient, time-pressured.
- **Super Admin** — verifies clinics and doctor licenses, triggers session generation. Occasional, back-office use.

## Product Purpose

A clinic management system: patients book and manage appointments, clinic staff run daily operations (scheduling, queues, walk-ins, cancellations, clinical documentation), and a Super Admin layer verifies clinics/doctors before they go live. Success = staff move through routine tasks with minimal friction, and patients trust the system enough to use it for their healthcare.

## Brand Personality

Warm human healthcare — approachable and calm, not cold or bureaucratic, but still unmistakably professional and clinical-grade. Both a rushed staff member and an anxious patient need to trust it with real medical operations and data. Think the warmth of Ro / Forward / One Medical, applied to a role-based operational tool rather than a consumer landing page.

## Anti-references

- **Generic AI-SaaS default** (what it looked like before this pass): default indigo-600 buttons, flat gray-200-bordered cards, no typographic scale, no personality.
- **Old-school enterprise EHR**: dense, ugly, "Windows-95 forms" hospital software feel.
- **Overly playful consumer app**: rounded blob illustrations, candy colors, cutesy tone — undermines clinical trust.

## Design Principles

1. **Trust through craft, not decoration** — precision and restraint read as clinical competence; the interface earns trust the way a well-run clinic does, not through flourish.
2. **Calm under pressure** — staff are often mid-task, mid-conversation; the UI should reduce cognitive load in the moment (clear hierarchy, obvious next action, low noise) rather than demand attention.
3. **Humane, not clinical-cold** — warmth lives in typography, color, and copy, not in decoration; healthcare should not feel bureaucratic.
4. **One system, three audiences** — patient/staff/admin surfaces share one visual language but can flex emphasis (patient = reassurance, staff = speed/density, admin = utility) without fracturing into different products.
5. **Accessible by default, considerate by design** — WCAG 2.1 AA is the floor; the patient-facing surface in particular should work well for older or less tech-savvy users (larger touch targets, higher contrast, plain language).

## Accessibility & Inclusion

WCAG 2.1 AA as the baseline across all surfaces. Extra consideration on the patient-facing surface specifically: larger touch targets, strong contrast (avoid light-gray-on-white body text), plain/simple language, forgiving flows (clear errors, undo where possible) for older or less tech-savvy patients. Respect `prefers-reduced-motion` throughout.
