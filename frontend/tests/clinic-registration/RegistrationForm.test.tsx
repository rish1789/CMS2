import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RegistrationForm } from '../../src/features/clinic-registration/RegistrationForm'
import { RegisterClinicApiError, registerClinic } from '../../src/features/clinic-registration/api'

vi.mock('../../src/features/clinic-registration/api', async () => {
  const actual = await vi.importActual<
    typeof import('../../src/features/clinic-registration/api')
  >('../../src/features/clinic-registration/api')
  return {
    ...actual,
    registerClinic: vi.fn(),
  }
})

const mockedRegisterClinic = vi.mocked(registerClinic)

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/clinic name/i), 'Sunrise Clinic')
  await user.type(screen.getByLabelText(/address/i), '12 MG Road, Pune')
  await user.type(screen.getByLabelText(/your name/i), 'Asha Rao')
  await user.type(screen.getByLabelText(/^email/i), 'asha@sunrise.example')
  await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
}

describe('RegistrationForm', () => {
  beforeEach(() => {
    mockedRegisterClinic.mockReset()
  })

  it('renders no Grievance Officer, billing/payment, or file-upload field', () => {
    render(<RegistrationForm />)

    expect(screen.queryByText(/grievance/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/billing/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/payment/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/subscription/i)).not.toBeInTheDocument()
    expect(document.querySelector('input[type="file"]')).not.toBeInTheDocument()
  })

  it('submits a valid payload and shows the success state with the generated staff code', async () => {
    const user = userEvent.setup()
    mockedRegisterClinic.mockResolvedValueOnce({
      clinicId: 'clinic-1',
      clinicName: 'Sunrise Clinic',
      verified: false,
      admin: { accountId: 'acct-1', email: 'asha@sunrise.example', staffCode: 'CA-4821' },
    })

    render(<RegistrationForm />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /register clinic/i }))

    await waitFor(() => {
      expect(screen.getByText(/clinic registered/i)).toBeInTheDocument()
    })
    expect(screen.getByText('CA-4821')).toBeInTheDocument()
    expect(mockedRegisterClinic).toHaveBeenCalledWith({
      clinic: {
        name: 'Sunrise Clinic',
        address: '12 MG Road, Pune',
        city: undefined,
        contactEmail: undefined,
        contactMobile: undefined,
      },
      admin: {
        name: 'Asha Rao',
        email: 'asha@sunrise.example',
        password: 'Str0ng!Pass',
        mobile: undefined,
      },
    })
  })

  it('shows a duplicate-email error next to the email field', async () => {
    const user = userEvent.setup()
    mockedRegisterClinic.mockRejectedValueOnce(
      new RegisterClinicApiError({
        error: 'EMAIL_ALREADY_IN_USE',
        message: 'This email is already registered to a staff account.',
      }),
    )

    render(<RegistrationForm />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /register clinic/i }))

    await waitFor(() => {
      expect(screen.getByText(/already registered to a staff account/i)).toBeInTheDocument()
    })
  })

  it('lists every failed password rule returned by the server', async () => {
    const user = userEvent.setup()
    mockedRegisterClinic.mockRejectedValueOnce(
      new RegisterClinicApiError({
        error: 'INVALID_PASSWORD',
        message: 'Password does not meet policy.',
        failedRules: ['minLength', 'uppercase', 'specialChar'],
      }),
    )

    render(<RegistrationForm />)
    await fillValidForm(user)
    await user.click(screen.getByRole('button', { name: /register clinic/i }))

    await waitFor(() => {
      expect(screen.getByText('minLength')).toBeInTheDocument()
    })
    expect(screen.getByText('uppercase')).toBeInTheDocument()
    expect(screen.getByText('specialChar')).toBeInTheDocument()
  })

  it('shows a mobile-format error next to the correct field', async () => {
    const user = userEvent.setup()
    mockedRegisterClinic.mockRejectedValueOnce(
      new RegisterClinicApiError({
        error: 'INVALID_MOBILE_NUMBER',
        field: 'admin.mobile',
        message: 'Mobile number must match the Indian numbering plan.',
      }),
    )

    render(<RegistrationForm />)
    await fillValidForm(user)
    await user.type(screen.getByLabelText(/^mobile \(optional\)$/i), '12345')
    await user.click(screen.getByRole('button', { name: /register clinic/i }))

    await waitFor(() => {
      expect(screen.getByText(/indian numbering plan/i)).toBeInTheDocument()
    })
  })
})
