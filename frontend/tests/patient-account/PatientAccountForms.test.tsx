import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SignupForm } from '../../src/features/patient-account/SignupForm'
import { LoginForm } from '../../src/features/patient-account/LoginForm'
import { ToastProvider } from '../../src/components/Toast'
import { MemoryRouter } from 'react-router-dom'
import {
  SignupPatientApiError,
  LoginPatientApiError,
  signupPatient,
  loginPatient,
} from '../../src/features/patient-account/api'

vi.mock('../../src/features/patient-account/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-account/api')>(
    '../../src/features/patient-account/api',
  )
  return {
    ...actual,
    signupPatient: vi.fn(),
    loginPatient: vi.fn(),
  }
})

const mockedSignupPatient = vi.mocked(signupPatient)
const mockedLoginPatient = vi.mocked(loginPatient)

describe('SignupForm', () => {
  beforeEach(() => {
    mockedSignupPatient.mockReset()
  })

  it('renders no social-login/SSO field and no staff-identity field', () => {
    render(<MemoryRouter><SignupForm /></MemoryRouter>)

    expect(screen.queryByText(/sign in with google/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sign in with facebook/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/single sign-on/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sso/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/role/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/clinic/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/staff code/i)).not.toBeInTheDocument()
  })

  it('submits a valid payload (email + password, no mobile) and shows the success state', async () => {
    const user = userEvent.setup()
    mockedSignupPatient.mockResolvedValueOnce({
      patientAccountId: 'patient-1',
      email: 'priya@example.com',
    })

    render(<MemoryRouter><SignupForm /></MemoryRouter>)
    await user.type(screen.getByLabelText(/^email/i), 'priya@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/account created/i)).toBeInTheDocument()
    })
    expect(mockedSignupPatient).toHaveBeenCalledWith({
      email: 'priya@example.com',
      password: 'Str0ng!Pass',
      mobile: undefined,
    })
  })

  it('submits with a valid mobile number when provided', async () => {
    const user = userEvent.setup()
    mockedSignupPatient.mockResolvedValueOnce({
      patientAccountId: 'patient-2',
      email: 'ravi@example.com',
    })

    render(<MemoryRouter><SignupForm /></MemoryRouter>)
    await user.type(screen.getByLabelText(/^email/i), 'ravi@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.type(screen.getByLabelText(/mobile/i), '9876543210')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(mockedSignupPatient).toHaveBeenCalledWith({
        email: 'ravi@example.com',
        password: 'Str0ng!Pass',
        mobile: '9876543210',
      })
    })
  })

  it('shows a duplicate-email error from the API', async () => {
    const user = userEvent.setup()
    mockedSignupPatient.mockRejectedValueOnce(
      new SignupPatientApiError({
        error: 'EMAIL_ALREADY_IN_USE',
        message: 'This email is already registered.',
      }),
    )

    render(<MemoryRouter><SignupForm /></MemoryRouter>)
    await user.type(screen.getByLabelText(/^email/i), 'dup@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/already registered/i)).toBeInTheDocument()
    })
  })

  it('shows every failed password rule from the API', async () => {
    const user = userEvent.setup()
    mockedSignupPatient.mockRejectedValueOnce(
      new SignupPatientApiError({
        error: 'INVALID_PASSWORD',
        message: 'Password does not satisfy the required policy',
        failedRules: ['minLength', 'uppercase', 'specialCharacter'],
      }),
    )

    render(<MemoryRouter><SignupForm /></MemoryRouter>)
    await user.type(screen.getByLabelText(/^email/i), 'weak@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'weak')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText('minLength')).toBeInTheDocument()
    })
    expect(screen.getByText('uppercase')).toBeInTheDocument()
    expect(screen.getByText('specialCharacter')).toBeInTheDocument()
  })

  it('shows an invalid-mobile-number error from the API', async () => {
    const user = userEvent.setup()
    mockedSignupPatient.mockRejectedValueOnce(
      new SignupPatientApiError({
        error: 'INVALID_MOBILE_NUMBER',
        message: 'Mobile number must match the Indian numbering plan.',
      }),
    )

    render(<MemoryRouter><SignupForm /></MemoryRouter>)
    await user.type(screen.getByLabelText(/^email/i), 'badmobile@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.type(screen.getByLabelText(/mobile/i), '12345')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/indian numbering plan/i)).toBeInTheDocument()
    })
  })
})

// 056-design-copy-quality-pass (login-page rebuild): LoginForm's pre-login heading is now
// itself "Welcome back." (matching design/patient-login-reference.html), and it now renders a
// real `<Link>` ("Create an account") plus a "Forgot password?" button wired to useToast - so
// tests need a Router + ToastProvider ancestor that the old, plainer form didn't require.
function renderLoginForm() {
  render(
    <MemoryRouter>
      <ToastProvider>
        <LoginForm />
      </ToastProvider>
    </MemoryRouter>,
  )
}

describe('LoginForm', () => {
  beforeEach(() => {
    mockedLoginPatient.mockReset()
  })

  it('renders no social-login/SSO field', () => {
    renderLoginForm()

    expect(screen.queryByText(/sign in with google/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sso/i)).not.toBeInTheDocument()
  })

  it('logs in successfully and shows the welcome state', async () => {
    const user = userEvent.setup()
    mockedLoginPatient.mockResolvedValueOnce({
      token: 'jwt-token',
      patientAccountId: 'patient-1',
      email: 'priya@example.com',
    })

    renderLoginForm()
    await user.type(screen.getByLabelText(/email/i), 'priya@example.com')
    await user.type(screen.getByLabelText(/password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    // Asserts the specific interpolated-email text from the post-login result branch, not the
    // generic /welcome back/i regex - the pre-login heading is now ALSO "Welcome back." (per
    // the reference design), so a loose regex would pass even without a real login happening.
    await waitFor(() => {
      expect(screen.getByText('Welcome back, priya@example.com.')).toBeInTheDocument()
    })
  })

  // 075-login-hardening (D-3C-2): one generic message for an unknown email and a wrong password -
  // the earlier separate messages told anyone which emails are registered.
  it('shows the one generic error for invalid credentials', async () => {
    const user = userEvent.setup()
    mockedLoginPatient.mockRejectedValueOnce(
      new LoginPatientApiError({ error: 'INVALID_CREDENTIALS', message: 'Incorrect email or password.' }),
    )

    renderLoginForm()
    await user.type(screen.getByLabelText(/email/i), 'unknown@example.com')
    await user.type(screen.getByLabelText(/password/i), 'whatever')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
    expect(screen.queryByText(/no account found/i)).not.toBeInTheDocument()
  })

  it('explains a temporary lockout with how long to wait', async () => {
    const user = userEvent.setup()
    mockedLoginPatient.mockRejectedValueOnce(
      new LoginPatientApiError({
        error: 'TOO_MANY_LOGIN_ATTEMPTS',
        message: 'Too many failed sign-in attempts. Please wait and try again.',
        retryAfterSeconds: 840,
      }),
    )

    renderLoginForm()
    await user.type(screen.getByLabelText(/email/i), 'priya@example.com')
    await user.type(screen.getByLabelText(/password/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many failed sign-in attempts/i)
    expect(alert).toHaveTextContent(/about 14 minutes/i)
  })

  it('shows an informational toast for "Forgot password?" instead of a dead link', async () => {
    const user = userEvent.setup()
    renderLoginForm()

    await user.click(screen.getByRole('button', { name: 'Forgot password?' }))

    expect(await screen.findByText(/password reset isn't available yet/i)).toBeInTheDocument()
  })

  it('links "Create an account" to the patient signup route', () => {
    renderLoginForm()

    expect(screen.getByRole('link', { name: 'Create an account' })).toHaveAttribute('href', '/patient/signup')
  })
})
