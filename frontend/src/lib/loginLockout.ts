// 075-login-hardening (D-3C-2): the 429 both login endpoints return after 5 failed attempts for one
// identifier - shared so the staff and patient sign-in forms word the wait identically.
import { formatRetryAfter } from './rateLimitMessage'

export interface LoginLockedBody {
  error: 'TOO_MANY_LOGIN_ATTEMPTS'
  message?: string
  retryAfterSeconds?: number
}

export function loginLockedMessage(body: LoginLockedBody): string {
  const base = body.message ?? 'Too many failed sign-in attempts. Please wait and try again.'
  return body.retryAfterSeconds == null ? base : `${base} Try again in ${formatRetryAfter(body.retryAfterSeconds)}.`
}
