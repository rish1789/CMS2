import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StaffLoginForm } from '../../src/features/staff-login/StaffLoginForm'
import { loginStaff, LoginStaffApiError } from '../../src/features/staff-login/api'
import { storeStaffSession } from '../../src/features/staff-login/token'
import { OnboardStaffForm } from '../../src/features/staff-onboarding/OnboardStaffForm'
import { onboardStaff } from '../../src/features/staff-onboarding/api'
import { ApiError } from '../../src/lib/apiClient'

// 065-phase1-stabilization (BUG-006): delay: null keeps every keystroke's events but drops
// user-event's per-keystroke setTimeout(0) yield, which queues behind other workers under
// full-suite parallel load (the cause of the intermittent 5 s timeouts in typing-heavy tests).
const TYPING_OPTIONS = { delay: null }

// staff-console-audit-2026-09-10 P2: OnboardStaffForm now renders a <Link> (the session-expiry
// "Sign in again" fix), so every render needs a Router in scope.
function renderOnboardStaffForm() {
  render(
    <MemoryRouter initialEntries={['/staff/clinics/clinic-1/onboard']}>
      <Routes>
        <Route path="/staff/clinics/:clinicId/onboard" element={<OnboardStaffForm clinicId="clinic-1" />} />
        <Route path="/staff/login" element={<div>Staff login page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

vi.mock('../../src/features/staff-login/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-login/api')>(
    '../../src/features/staff-login/api',
  )
  return { ...actual, loginStaff: vi.fn() }
})

vi.mock('../../src/features/staff-onboarding/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-onboarding/api')>(
    '../../src/features/staff-onboarding/api',
  )
  return { ...actual, onboardStaff: vi.fn() }
})

const mockedLoginStaff = vi.mocked(loginStaff)
const mockedOnboardStaff = vi.mocked(onboardStaff)

describe('StaffLoginForm', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedLoginStaff.mockReset()
  })

  it('logs in and stores the session on success', async () => {
    mockedLoginStaff.mockResolvedValueOnce({
      token: 'jwt-token',
      accountId: 'account-1',
      email: 'admin@clinic.example',
      role: 'STAFF',
    })
    const onSuccess = vi.fn()
    const user = userEvent.setup(TYPING_OPTIONS)
    render(<StaffLoginForm onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText(/email/i), 'admin@clinic.example')
    await user.type(screen.getByLabelText(/password/i), 'S3cret!23')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    // 040-super-admin-rbac-login: onSuccess now reports the resolved role, not the
    // full session - the caller (StaffLoginPage) uses it to decide where to navigate.
    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith({ role: 'STAFF' })
    })
    expect(JSON.parse(sessionStorage.getItem('cms.staffToken')!)).toEqual({
      token: 'jwt-token',
      accountId: 'account-1',
      email: 'admin@clinic.example',
    })
  })

  it('shows an error on invalid credentials', async () => {
    mockedLoginStaff.mockRejectedValueOnce(new LoginStaffApiError({ error: 'UNAUTHORIZED' }))
    const user = userEvent.setup(TYPING_OPTIONS)
    render(<StaffLoginForm />)

    await user.type(screen.getByLabelText(/email/i), 'admin@clinic.example')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid email or password/i)
  })
})

describe('OnboardStaffForm', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedOnboardStaff.mockReset()
  })

  it('prompts to sign in when no staff session exists', () => {
    renderOnboardStaffForm()
    expect(screen.getByRole('alert')).toHaveTextContent(/sign in as a clinicadmin/i)
  })

  it('onboards an Operations hire and shows the one-time credentials', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockResolvedValueOnce({
      accountId: 'new-1',
      email: 'ops@clinic.example',
      staffCode: 'OP-1234',
      temporaryPassword: 'Tmp!2345',
      role: 'Operations',
      doctorProfileId: null,
    })
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    await waitFor(() => {
      expect(mockedOnboardStaff).toHaveBeenCalledWith(
        'clinic-1',
        expect.objectContaining({ name: 'Jane Ops', email: 'ops@clinic.example', role: 'Operations' }),
        'jwt-token',
      )
    })
    expect(await screen.findByText('OP-1234')).toBeInTheDocument()
    expect(screen.getByText('Tmp!2345')).toBeInTheDocument()
  })

  it('shows Doctor-specific fields and submits them when role=Doctor', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockResolvedValueOnce({
      accountId: 'new-2',
      email: 'doc@clinic.example',
      staffCode: 'DR-4821',
      temporaryPassword: 'Tmp!6789',
      role: 'Doctor',
      doctorProfileId: 'profile-1',
    })
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Dr. Sharma')
    await user.type(screen.getByLabelText(/^email/i), 'doc@clinic.example')
    await user.selectOptions(screen.getByLabelText(/role/i), 'Doctor')

    expect(screen.getByLabelText(/specialization/i)).toBeInTheDocument()

    await user.type(screen.getByLabelText(/specialization/i), 'Cardiology')
    await user.type(screen.getByLabelText(/license number/i), 'LIC-999')
    await user.type(screen.getByLabelText(/experience/i), '5')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    await waitFor(() => {
      expect(mockedOnboardStaff).toHaveBeenCalledWith(
        'clinic-1',
        expect.objectContaining({
          role: 'Doctor',
          doctor: { specialization: 'Cardiology', licenseNumber: 'LIC-999', experienceYears: 5 },
        }),
        'jwt-token',
      )
    })
    expect(await screen.findByText('DR-4821')).toBeInTheDocument()
  })

  // 054-forms-validation-consistency T016: MISSING_REQUIRED_FIELD naming a field with a real
  // FormField slot (email) renders at that field, not the top-level banner (FR-005/FR-006).
  // EMAIL_ALREADY_IN_USE below (a field-agnostic error, no `field` on the body) already proves
  // the "everything else stays a banner" half of the same rule.
  it('shows a MISSING_REQUIRED_FIELD error at the email field, not the banner', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockRejectedValueOnce(
      new ApiError(400, 'A required field is missing or invalid', {
        error: 'MISSING_REQUIRED_FIELD',
        field: 'email',
      }),
    )
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/required field is missing/i)
  })

  // 054-forms-validation-consistency T016 (SC-003's other half): a server-driven
  // INVALID_MOBILE_NUMBER (not the client-side regex - mobile is left blank, which the client
  // check never flags) still maps to the mobile field's FormField slot, not the banner.
  it('shows a server-driven INVALID_MOBILE_NUMBER error at the mobile field, not the banner', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockRejectedValueOnce(
      new ApiError(400, 'Mobile number does not match the Indian numbering plan', {
        error: 'INVALID_MOBILE_NUMBER',
        field: 'mobile',
      }),
    )
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/indian numbering plan/i)
  })

  it('surfaces a duplicate-email server error', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockRejectedValueOnce(
      new ApiError(409, 'This email is already in use by another staff account.', {
        error: 'EMAIL_ALREADY_IN_USE',
      }),
    )
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already in use/i)
  })

  it('clears the session and prompts re-sign-in on a 401 response', async () => {
    storeStaffSession({ token: 'expired-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockRejectedValueOnce(
      new ApiError(401, 'Your session has expired. Please sign in again.', { error: 'UNAUTHORIZED' }),
    )
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/sign in again/i)
    expect(sessionStorage.getItem('cms.staffToken')).toBeNull()

    // staff-console-audit-2026-09-10 P2: this used to be a dead end - the message alone, no way
    // back to sign in short of the browser's own back button.
    const signInLink = screen.getByRole('link', { name: /sign in again/i })
    expect(signInLink).toHaveAttribute('href', '/staff/login')
    await user.click(signInLink)
    expect(await screen.findByText('Staff login page')).toBeInTheDocument()
  })

  /** staff-console-redesign-2026-09-10: a real way back out, not just the browser's own back button. */
  it('offers a Cancel link back to the clinic dashboard', () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    renderOnboardStaffForm()

    expect(screen.getByRole('link', { name: /cancel/i })).toHaveAttribute('href', '/staff/clinics/clinic-1')
  })

  it('only offers Doctor and Operations as roles - matches what the backend actually accepts', () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    renderOnboardStaffForm()

    const options = screen.getAllByRole('option').map((option) => option.textContent)
    expect(options).toEqual(['Operations', 'Doctor'])
  })

  // 054-forms-validation-consistency T014: pre-submit Indian-mobile-format check blocks the
  // network call entirely (research.md Decision 3) - name/email survive the failed attempt.
  it('blocks submission and shows an inline error when the mobile number is not a valid Indian number', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Jane Ops')
    await user.type(screen.getByLabelText(/^email/i), 'ops@clinic.example')
    await user.type(screen.getByLabelText(/mobile/i), '12345')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/valid indian number/i)
    expect(mockedOnboardStaff).not.toHaveBeenCalled()
    expect(screen.getByLabelText(/^name/i)).toHaveValue('Jane Ops')
    expect(screen.getByLabelText(/^email/i)).toHaveValue('ops@clinic.example')
  })

  it('shows a distinct message (no temporary password) when the doctor already has an account elsewhere', async () => {
    storeStaffSession({ token: 'jwt-token', accountId: 'admin-1', email: 'admin@clinic.example' })
    mockedOnboardStaff.mockResolvedValueOnce({
      accountId: 'new-3',
      email: 'existing.doctor@clinic.example',
      staffCode: 'DR-2002',
      temporaryPassword: null,
      role: 'Doctor',
      doctorProfileId: 'doctor-profile-1',
      existingAccount: true,
    })
    const user = userEvent.setup(TYPING_OPTIONS)
    renderOnboardStaffForm()

    await user.type(screen.getByLabelText(/^name/i), 'Dr. Existing')
    await user.type(screen.getByLabelText(/^email/i), 'existing.doctor@clinic.example')
    await user.selectOptions(screen.getByLabelText(/role/i), 'Doctor')
    await user.type(screen.getByLabelText(/specialization/i), 'Cardiology')
    await user.type(screen.getByLabelText(/license number/i), 'LIC-1')
    await user.type(screen.getByLabelText(/experience/i), '5')
    await user.click(screen.getByRole('button', { name: /onboard staff/i }))

    expect(await screen.findByText(/already has an account/i)).toBeInTheDocument()
    expect(screen.getByText('DR-2002')).toBeInTheDocument()
    expect(screen.queryByText('Temporary password')).not.toBeInTheDocument()
  })
})
