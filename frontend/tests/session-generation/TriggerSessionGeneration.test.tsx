import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { TriggerSessionGeneration } from '../../src/features/session-generation/TriggerSessionGeneration'
import { generateSessions, AdminApiError } from '../../src/features/session-generation/api'
import { storeSuperAdminSession } from '../../src/features/super-admin/token'

vi.mock('../../src/features/session-generation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-generation/api')>(
    '../../src/features/session-generation/api',
  )
  return {
    ...actual,
    generateSessions: vi.fn(),
  }
})

const mockedGenerateSessions = vi.mocked(generateSessions)

// 040-super-admin-rbac-login: this screen no longer collects its own credentials - a
// Super Admin session (established at the Clinic Portal, /staff/login) is expected to
// already exist by the time RequireSuperAdminSession lets a visitor reach this route.
function renderWithSession() {
  storeSuperAdminSession({ token: 'super-admin-jwt', username: 'super-admin' })
  render(
    <MemoryRouter initialEntries={['/super-admin-console/sessions/generate']}>
      <Routes>
        <Route path="/staff/login" element={<div>Clinic sign in</div>} />
        <Route path="/super-admin-console/sessions/generate" element={<TriggerSessionGeneration />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('TriggerSessionGeneration', () => {
  beforeEach(() => {
    mockedGenerateSessions.mockReset()
    sessionStorage.clear()
  })

  it('shows the created count after a successful trigger, calling the API with the stored session token', async () => {
    const user = userEvent.setup()
    mockedGenerateSessions.mockResolvedValueOnce({ runDate: '2026-09-03', sessionsCreated: 7 })

    renderWithSession()
    await user.click(screen.getByRole('button', { name: /generate sessions now/i }))

    const message = await screen.findByText(/7 sessions created for/i)
    expect(message).toHaveTextContent(/thursday/i)
    expect(message).toHaveTextContent(/september/i)
    expect(message).toHaveTextContent(/3/)
    expect(mockedGenerateSessions).toHaveBeenCalledWith('super-admin-jwt')
  })

  it('040-super-admin-rbac-login (US3): redirects to the Clinic Portal login and clears the session on a 401 response', async () => {
    const user = userEvent.setup()
    mockedGenerateSessions.mockRejectedValueOnce(new AdminApiError(401))

    renderWithSession()
    await user.click(screen.getByRole('button', { name: /generate sessions now/i }))

    await waitFor(() => {
      expect(screen.getByText('Clinic sign in')).toBeInTheDocument()
    })
    expect(sessionStorage.getItem('cms.superAdminToken')).toBeNull()
  })
})
