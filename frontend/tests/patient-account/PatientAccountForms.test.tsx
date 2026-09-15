import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SignupForm } from '../../src/features/patient-account/SignupForm'
import { LoginForm } from '../../src/features/patient-account/LoginForm'
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
    render(<SignupForm />)

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

    render(<SignupForm />)
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

    render(<SignupForm />)
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

    render(<SignupForm />)
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

    render(<SignupForm />)
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

    render(<SignupForm />)
    await user.type(screen.getByLabelText(/^email/i), 'badmobile@example.com')
    await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
    await user.type(screen.getByLabelText(/mobile/i), '12345')
    await user.click(screen.getByRole('button', { name: /create account/i }))

    await waitFor(() => {
      expect(screen.getByText(/indian numbering plan/i)).toBeInTheDocument()
    })
  })
})

describe('LoginForm', () => {
  beforeEach(() => {
    mockedLoginPatient.mockReset()
  })

  it('renders no social-login/SSO field', () => {
    render(<LoginForm />)

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

    render(<LoginForm />)
    await user.type(screen.getByLabelText(/email/i), 'priya@example.com')
    await user.type(screen.getByLabelText(/password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(screen.getByText(/welcome back/i)).toBeInTheDocument()
    })
  })

  it('shows an identical error for wrong password and unknown email (no information leak)', async () => {
    const user = userEvent.setup()
    mockedLoginPatient.mockRejectedValueOnce(
      new LoginPatientApiError({ error: 'INVALID_CREDENTIALS', message: 'Invalid email or password.' }),
    )

    render(<LoginForm />)
    await user.type(screen.getByLabelText(/email/i), 'unknown@example.com')
    await user.type(screen.getByLabelText(/password/i), 'whatever')
    await user.click(screen.getByRole('button', { name: /log in/i }))

    await waitFor(() => {
      expect(screen.getByText(/invalid email or password/i)).toBeInTheDocument()
    })
  })
})
