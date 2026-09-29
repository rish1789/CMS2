// 060-booking-abuse-prevention (spec.md FR-011): formats a RATE_LIMITED response's
// retryAfterSeconds into the "including approximately how much longer" wait-time text every
// rate-limited booking surface needs - shared so the patient-facing wording stays identical
// across the fixed-time and queue booking flows.

export function formatRetryAfter(retryAfterSeconds: number): string {
  if (retryAfterSeconds < 60) {
    return 'less than a minute'
  }
  const minutes = Math.ceil(retryAfterSeconds / 60)
  return minutes === 1 ? 'about 1 minute' : `about ${minutes} minutes`
}

export function rateLimitMessage(retryAfterSeconds: number | undefined): string {
  const base = "You've made a few too many booking attempts in a short time - please wait before trying again."
  if (retryAfterSeconds == null) {
    return base
  }
  return `${base} Try again in ${formatRetryAfter(retryAfterSeconds)}.`
}
