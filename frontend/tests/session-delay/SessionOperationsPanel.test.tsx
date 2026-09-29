import { render, screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { SessionOperationsPanel } from '../../src/features/session-delay/SessionOperationsPanel'
import { storeStaffSession } from '../../src/features/staff-login/token'

const CLINIC_ID = 'clinic-1'
const SESSION_ID = 'session-1'
const SLOT_ID = 'slot-1'

describe('SessionOperationsPanel', () => {
  beforeEach(() => {
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'staff@example.com' })
  })

  it('shows Mark appeared for a non-doctor caller', () => {
    render(
      <SessionOperationsPanel clinicId={CLINIC_ID} sessionId={SESSION_ID} slotId={SLOT_ID} isDoctor={false} />,
    )

    expect(screen.getByRole('button', { name: /^appeared$/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^completed$/i })).toBeInTheDocument()
  })

  it('hides Mark appeared for a doctor caller (FR-007) but keeps Mark completed (FR-006)', () => {
    render(
      <SessionOperationsPanel clinicId={CLINIC_ID} sessionId={SESSION_ID} slotId={SLOT_ID} isDoctor={true} />,
    )

    expect(screen.queryByRole('button', { name: /^appeared$/i })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /^completed$/i })).toBeInTheDocument()
  })
})
