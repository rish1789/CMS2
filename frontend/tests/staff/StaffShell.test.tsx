import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { StaffShell } from '../../src/routes/staff/StaffShell'
import { storeStaffSession } from '../../src/features/staff-login/token'

function renderShell(initialEntry = '/staff') {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/staff" element={<StaffShell />}>
          <Route index element={<div>picker content</div>} />
        </Route>
        <Route path="/staff/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

// 056-design-copy-quality-pass: StaffShell used to wrap its <Outlet/> in `mx-auto max-w-5xl`,
// the outer layer of the three nested centered boxes that caused the wasted-space complaint.
// That cap is removed here - MyClinicsList (the sidebar-less /staff picker) now carries its
// own equivalent wrapper (see MyClinicsList.tsx), and ClinicShell (the sidebar-bearing routes)
// needs the full width for its edge-docked sidebar (research.md Decision 5).
describe('StaffShell', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('renders the outlet content with no width-constraining wrapper on <main>', () => {
    renderShell()

    expect(screen.getByText('picker content')).toBeInTheDocument()
    const main = document.querySelector('main')
    expect(main).not.toBeNull()
    expect(main).not.toHaveClass('mx-auto')
    expect(main).not.toHaveClass('max-w-5xl')
  })

  it('shows the brand link and no session indicator when signed out', () => {
    renderShell()

    expect(screen.getByRole('link', { name: /Staff console/ })).toHaveAttribute('href', '/staff')
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  it('shows the signed-in email and signs out via the button', async () => {
    storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'dr.sharma@clinic.example' })
    renderShell()

    expect(screen.getByText('dr.sharma@clinic.example')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('login page')).toBeInTheDocument()
    expect(sessionStorage.getItem('cms.staffToken')).toBeNull()
  })
})
