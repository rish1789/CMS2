import { describe, expect, it } from 'vitest'
import { decideClinicPortalDestination } from '../../src/features/staff-login/destination'

describe('decideClinicPortalDestination (040-super-admin-rbac-login FR-011)', () => {
  it('sends SUPER_ADMIN to the console', () => {
    expect(decideClinicPortalDestination('SUPER_ADMIN')).toBe('/super-admin-console')
  })

  it('sends STAFF to the staff dashboard, unchanged', () => {
    expect(decideClinicPortalDestination('STAFF')).toBe('/staff')
  })
})
