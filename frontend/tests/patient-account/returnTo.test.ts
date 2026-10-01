import { describe, expect, it } from 'vitest'
import { returnPathFrom, safeReturnPath, withReturnTo } from '../../src/features/patient-account/returnTo'

// 070-login-return-path (live-audit finding 4): only internal patient/discovery destinations are
// ever followed after login - never another origin, and never back into login/signup.
describe('safeReturnPath', () => {
  it.each([
    ['/patient/clinics/c1?doctorId=d1', '/patient/clinics/c1?doctorId=d1'],
    ['/patient/clinics/c1?doctorId=d1#slots', '/patient/clinics/c1?doctorId=d1#slots'],
    ['/patient', '/patient'],
    ['/patient/bookings/b1', '/patient/bookings/b1'],
    ['/discover?q=cardiology', '/discover?q=cardiology'],
  ])('accepts %s', (raw, expected) => {
    expect(safeReturnPath(raw)).toBe(expected)
  })

  it.each([
    '//evil.example.com/patient',
    '/\\evil.example.com',
    'https://evil.example.com/patient',
    'javascript:alert(1)',
    '/patient/login',
    '/patient/login?returnTo=/patient',
    '/patient/signup?returnTo=/patient',
    '/patientx',
    '/staff/clinics/c1',
    '/super-admin-console',
    'patient/clinics/c1',
    '',
    '/patient/clinics/c1\n',
    '/patient/\u0000x',
    42,
    null,
    undefined,
  ])('rejects %j', (raw) => {
    expect(safeReturnPath(raw)).toBeNull()
  })
})

describe('returnPathFrom', () => {
  it('prefers the guard location (path, query and hash)', () => {
    const state = { from: { pathname: '/patient/clinics/c1', search: '?doctorId=d1', hash: '#top' } }
    expect(returnPathFrom(state, '?returnTo=%2Fdiscover')).toBe('/patient/clinics/c1?doctorId=d1#top')
  })

  it('falls back to the returnTo query parameter', () => {
    expect(returnPathFrom(null, '?returnTo=%2Fpatient%2Fclinics%2Fc1%3FdoctorId%3Dd1')).toBe(
      '/patient/clinics/c1?doctorId=d1',
    )
  })

  it('returns null for an unsafe or missing destination', () => {
    expect(returnPathFrom({ from: { pathname: '//evil.example.com' } }, '')).toBeNull()
    expect(returnPathFrom(undefined, '?returnTo=https%3A%2F%2Fevil.example.com')).toBeNull()
    expect(returnPathFrom(undefined, '')).toBeNull()
  })
})

describe('withReturnTo', () => {
  it('appends an encoded returnTo only when there is one', () => {
    expect(withReturnTo('/patient/signup', '/patient/clinics/c1?doctorId=d1')).toBe(
      '/patient/signup?returnTo=%2Fpatient%2Fclinics%2Fc1%3FdoctorId%3Dd1',
    )
    expect(withReturnTo('/patient/signup', null)).toBe('/patient/signup')
  })
})
