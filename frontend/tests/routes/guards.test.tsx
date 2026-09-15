import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { RequireSuperAdminSession } from '../../src/routes/guards'
import { storeSuperAdminSession } from '../../src/features/super-admin/token'
import { storeStaffSession } from '../../src/features/staff-login/token'

function renderGuarded(initialPath = '/super-admin-console') {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/staff/login" element={<div>Clinic sign in</div>} />
        <Route element={<RequireSuperAdminSession />}>
          <Route path="/super-admin-console" element={<div>Super Admin console content</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireSuperAdminSession (040-super-admin-rbac-login T030/US3)', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('redirects to the Clinic Portal login when no Super Admin session exists', () => {
    renderGuarded()

    expect(screen.getByText('Clinic sign in')).toBeInTheDocument()
    expect(screen.queryByText('Super Admin console content')).not.toBeInTheDocument()
  })

  it('redirects to the Clinic Portal login when a staff session exists but no Super Admin session', () => {
    storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })

    renderGuarded()

    expect(screen.getByText('Clinic sign in')).toBeInTheDocument()
    expect(screen.queryByText('Super Admin console content')).not.toBeInTheDocument()
  })

  it('renders the console when a valid Super Admin session exists', () => {
    storeSuperAdminSession({ token: 'super-admin-jwt', username: 'super-admin' })

    renderGuarded()

    expect(screen.getByText('Super Admin console content')).toBeInTheDocument()
  })
})
