// 070-login-return-path (live-audit finding 4): where a patient goes after login/signup. Only an
// internal patient or discovery path is ever followed - never another origin (protocol-relative,
// scheme, backslash tricks) and never back into login/signup (an authentication loop).

const AUTH_PATHS = new Set(['/patient/login', '/patient/signup'])

function hasControlCharacter(value: string): boolean {
  for (let i = 0; i < value.length; i++) {
    const code = value.charCodeAt(i)
    if (code < 0x20 || code === 0x7f) return true
  }
  return false
}

/** The validated internal destination, or null when it is missing or unsafe. */
export function safeReturnPath(raw: unknown): string | null {
  if (typeof raw !== 'string' || raw.length === 0) return null
  if (!raw.startsWith('/') || raw.startsWith('//') || raw.includes('\\') || hasControlCharacter(raw)) {
    return null
  }
  const pathname = raw.split(/[?#]/, 1)[0]
  if (AUTH_PATHS.has(pathname)) return null
  const allowed = pathname === '/patient' || pathname.startsWith('/patient/') || pathname === '/discover'
  return allowed ? raw : null
}

interface GuardLocation {
  pathname?: unknown
  search?: unknown
  hash?: unknown
}

/** The guard's original location (`state.from`) first, else the `?returnTo=` query parameter. */
export function returnPathFrom(state: unknown, search: string): string | null {
  const from = (state as { from?: GuardLocation } | null | undefined)?.from
  if (from && typeof from.pathname === 'string') {
    const candidate = `${from.pathname}${typeof from.search === 'string' ? from.search : ''}${
      typeof from.hash === 'string' ? from.hash : ''
    }`
    return safeReturnPath(candidate)
  }
  return safeReturnPath(new URLSearchParams(search).get('returnTo'))
}

/** A login/signup link that carries the destination across the hop. */
export function withReturnTo(path: string, returnTo: string | null | undefined): string {
  return returnTo ? `${path}?returnTo=${encodeURIComponent(returnTo)}` : path
}
