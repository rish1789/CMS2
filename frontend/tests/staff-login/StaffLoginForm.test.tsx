import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { StaffLoginForm } from '../../src/features/staff-login/StaffLoginForm'
import { loginStaff, LoginStaffApiError } from '../../src/features/staff-login/api'

vi.mock('../../src/features/staff-login/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/staff-login/api')>(
    '../../src/features/staff-login/api',
  )
  return { ...actual, loginStaff: vi.fn() }
})

const mockedLoginStaff = vi.mocked(loginStaff)

describe('StaffLoginForm - staff code identifier (T004)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedLoginStaff.mockReset()
  })

  it('submits a staff-code-shaped value as the identifier field', async () => {
    mockedLoginStaff.mockResolvedValueOnce({
      token: 'jwt-token',
      accountId: 'account-1',
      email: 'dr.sharma@clinic.example',
      role: 'STAFF',
    })
    const user = userEvent.setup()
    render(<StaffLoginForm />)

    await user.type(screen.getByLabelText(/email or staff code/i), 'DR-4821')
    await user.type(screen.getByLabelText(/password/i), 'S3cret!23')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockedLoginStaff).toHaveBeenCalledWith({ identifier: 'DR-4821', password: 'S3cret!23' })
    })
  })

  it('submits an email-shaped value identically as the identifier field', async () => {
    mockedLoginStaff.mockResolvedValueOnce({
      token: 'jwt-token',
      accountId: 'account-1',
      email: 'dr.sharma@clinic.example',
      role: 'STAFF',
    })
    const user = userEvent.setup()
    render(<StaffLoginForm />)

    await user.type(screen.getByLabelText(/email or staff code/i), 'dr.sharma@clinic.example')
    await user.type(screen.getByLabelText(/password/i), 'S3cret!23')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(mockedLoginStaff).toHaveBeenCalledWith({
        identifier: 'dr.sharma@clinic.example',
        password: 'S3cret!23',
      })
    })
  })

  it('surfaces the same error on an unrecognized staff code as an unrecognized email', async () => {
    mockedLoginStaff.mockRejectedValueOnce(new LoginStaffApiError({ error: 'UNAUTHORIZED' }))
    const user = userEvent.setup()
    render(<StaffLoginForm />)

    await user.type(screen.getByLabelText(/email or staff code/i), 'DR-0000')
    await user.type(screen.getByLabelText(/password/i), 'wrong')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid/i)
  })
})

describe('StaffLoginForm - Super Admin role resolution (040-super-admin-rbac-login)', () => {
  beforeEach(() => {
    sessionStorage.clear()
    mockedLoginStaff.mockReset()
  })

  it('T012 [US1]: stores a Super Admin session (not a staff session) and reports role SUPER_ADMIN', async () => {
    mockedLoginStaff.mockResolvedValueOnce({
      token: 'super-admin-jwt',
      accountId: null,
      email: 'super-admin',
      role: 'SUPER_ADMIN',
    })
    const onSuccess = vi.fn()
    const user = userEvent.setup()
    render(<StaffLoginForm onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText(/email or staff code/i), 'super-admin')
    await user.type(screen.getByLabelText(/password/i), 'Str0ng!Pass')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith({ role: 'SUPER_ADMIN' })
    })
    expect(sessionStorage.getItem('cms.superAdminToken')).toContain('super-admin-jwt')
    expect(sessionStorage.getItem('cms.staffToken')).toBeNull()
  })

  it('T024 [US2]: stores a staff session (not a Super Admin session) and reports role STAFF, unchanged', async () => {
    mockedLoginStaff.mockResolvedValueOnce({
      token: 'staff-jwt',
      accountId: 'account-1',
      email: 'dr.sharma@clinic.example',
      role: 'STAFF',
    })
    const onSuccess = vi.fn()
    const user = userEvent.setup()
    render(<StaffLoginForm onSuccess={onSuccess} />)

    await user.type(screen.getByLabelText(/email or staff code/i), 'dr.sharma@clinic.example')
    await user.type(screen.getByLabelText(/password/i), 'S3cret!23')
    await user.click(screen.getByRole('button', { name: /sign in/i }))

    await waitFor(() => {
      expect(onSuccess).toHaveBeenCalledWith({ role: 'STAFF' })
    })
    expect(sessionStorage.getItem('cms.staffToken')).toContain('staff-jwt')
    expect(sessionStorage.getItem('cms.superAdminToken')).toBeNull()
  })
})
