import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it } from 'vitest'
import { PatientShell } from '../../../src/routes/patient/PatientShell'
import { storePatientSession } from '../../../src/features/patient-account/token'

function renderShell(initialEntry = '/patient') {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/patient" element={<PatientShell />}>
          <Route index element={<div>dashboard content</div>} />
        </Route>
        <Route path="/patient/login" element={<div>login page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('PatientShell', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('shows the brand and no account menu when signed out', () => {
    renderShell()

    expect(screen.getByText('CMS2 Clinic Management')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Sign out' })).not.toBeInTheDocument()
  })

  it("shows an avatar initial derived from the signed-in patient's email, and the email itself", () => {
    storePatientSession({ token: 'jwt', patientAccountId: 'account-1', email: 'priya.sharma@example.com' })
    renderShell()

    expect(screen.getByText('P')).toBeInTheDocument()
    expect(screen.getByText('priya.sharma@example.com')).toBeInTheDocument()
  })

  it('signs out and navigates to the patient login page', async () => {
    const user = userEvent.setup()
    storePatientSession({ token: 'jwt', patientAccountId: 'account-1', email: 'priya@example.com' })
    renderShell()

    await user.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByText('login page')).toBeInTheDocument()
    expect(sessionStorage.getItem('cms.patientToken')).toBeNull()
  })
})
