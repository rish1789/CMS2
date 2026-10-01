import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RequirePatientSession } from '../../../src/routes/guards'
import { PatientLoginPage } from '../../../src/routes/patient/PatientLoginPage'
import { PatientSignupPage } from '../../../src/routes/patient/PatientSignupPage'
import { ToastProvider } from '../../../src/components/Toast'
import { loginPatient, signupPatient } from '../../../src/features/patient-account/api'

vi.mock('../../../src/features/patient-account/api', async () => {
  const actual = await vi.importActual<typeof import('../../../src/features/patient-account/api')>(
    '../../../src/features/patient-account/api',
  )
  return { ...actual, loginPatient: vi.fn(), signupPatient: vi.fn() }
})

const mockedLogin = vi.mocked(loginPatient)
const mockedSignup = vi.mocked(signupPatient)

function WhereAmI() {
  const location = useLocation()
  return <p data-testid="location">{`${location.pathname}${location.search}${location.hash}`}</p>
}

function renderApp(initialPath: string) {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <ToastProvider>
        <Routes>
          <Route path="/patient/login" element={<PatientLoginPage />} />
          <Route path="/patient/signup" element={<PatientSignupPage />} />
          <Route element={<RequirePatientSession />}>
            <Route path="/patient/*" element={<WhereAmI />} />
          </Route>
          <Route path="*" element={<WhereAmI />} />
        </Routes>
      </ToastProvider>
    </MemoryRouter>,
  )
}

async function logIn(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/email/i), 'priya@example.com')
  await user.type(screen.getByLabelText(/password/i), 'Str0ng!Pass')
  await user.click(screen.getByRole('button', { name: /log in/i }))
}

// 070-login-return-path (live-audit finding 4).
describe('patient login return path', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedLogin.mockReset()
    mockedSignup.mockReset()
    mockedLogin.mockResolvedValue({ token: 'jwt', patientAccountId: 'pa-1', email: 'priya@example.com' })
  })

  it('returns to the selected clinic and doctor after login', async () => {
    const user = userEvent.setup()
    renderApp('/patient/clinics/clinic-1?doctorId=doctor-9')

    await logIn(user)

    expect(await screen.findByTestId('location')).toHaveTextContent('/patient/clinics/clinic-1?doctorId=doctor-9')
  })

  it('goes to the dashboard after a direct login', async () => {
    const user = userEvent.setup()
    renderApp('/patient/login')

    await logIn(user)

    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/patient$/)
  })

  it.each(['//evil.example.com/x', 'https://evil.example.com', '/patient/login', '/patient/signup'])(
    'ignores an unsafe returnTo (%s) and goes to the dashboard',
    async (target) => {
      const user = userEvent.setup()
      renderApp(`/patient/login?returnTo=${encodeURIComponent(target)}`)

      await logIn(user)

      expect(await screen.findByTestId('location')).toHaveTextContent(/^\/patient$/)
    },
  )

  it('keeps the destination through login → signup → login', async () => {
    const user = userEvent.setup()
    mockedSignup.mockResolvedValueOnce({ patientAccountId: 'pa-1', email: 'priya@example.com' })
    renderApp('/patient/clinics/clinic-1?doctorId=doctor-9')

    await user.click(screen.getByRole('link', { name: /create an account/i }))
    await user.type(screen.getByLabelText(/email/i), 'priya@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /create account|sign up/i }))
    await user.click(await screen.findByRole('link', { name: /log in/i }))
    await logIn(user)

    expect(await screen.findByTestId('location')).toHaveTextContent('/patient/clinics/clinic-1?doctorId=doctor-9')
  })
})
