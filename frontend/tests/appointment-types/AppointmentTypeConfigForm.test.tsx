import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppointmentTypeConfigForm } from '../../src/features/appointment-types/AppointmentTypeConfigForm'
import { listAppointmentTypes, renameAppointmentType } from '../../src/features/appointment-types/api'
import { AppointmentTypeApiError } from '../../src/features/appointment-types/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/appointment-types/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
    '../../src/features/appointment-types/api',
  )
  return {
    ...actual,
    listAppointmentTypes: vi.fn(),
    renameAppointmentType: vi.fn(),
  }
})

const mockedList = vi.mocked(listAppointmentTypes)
const mockedRename = vi.mocked(renameAppointmentType)

const DOCTOR_PROFILE_ID = 'doctor-1'

// real-bug-fix 2026-09-17: covers the fix for "created an appointment type with the wrong name
// and had no way to correct it" - create/list previously had no update capability at all.
describe('AppointmentTypeConfigForm - editing an existing appointment type', () => {
  beforeEach(() => {
    mockedList.mockReset()
    mockedRename.mockReset()
    mockedList.mockResolvedValue([{ id: 'type-1', doctorProfileId: DOCTOR_PROFILE_ID, name: 'Gauresh Kumar', feeOverride: null }])
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'admin@example.com' })
  })

  it('lets staff correct a mistyped name via the Edit action', async () => {
    const user = userEvent.setup()
    mockedRename.mockResolvedValueOnce({
      id: 'type-1',
      doctorProfileId: DOCTOR_PROFILE_ID,
      name: 'General Consultation',
      feeOverride: null,
    })
    mockedList.mockResolvedValueOnce([
      { id: 'type-1', doctorProfileId: DOCTOR_PROFILE_ID, name: 'Gauresh Kumar', feeOverride: null },
    ])
    mockedList.mockResolvedValueOnce([
      { id: 'type-1', doctorProfileId: DOCTOR_PROFILE_ID, name: 'General Consultation', feeOverride: null },
    ])

    render(<AppointmentTypeConfigForm doctorProfileId={DOCTOR_PROFILE_ID} />)

    await screen.findByText('Gauresh Kumar')
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const editForm = within(screen.getByRole('form', { name: 'Edit Gauresh Kumar' }))
    const nameInput = editForm.getByLabelText('Appointment type name')
    expect(nameInput).toHaveValue('Gauresh Kumar')
    await user.clear(nameInput)
    await user.type(nameInput, 'General Consultation')
    await user.click(editForm.getByRole('button', { name: 'Save' }))

    expect(mockedRename).toHaveBeenCalledWith(
      DOCTOR_PROFILE_ID,
      'type-1',
      { name: 'General Consultation', feeOverride: undefined },
      'a.jwt.token',
    )
    expect(await screen.findByText('Appointment type updated.')).toBeInTheDocument()
    expect(await screen.findByText('General Consultation')).toBeInTheDocument()
  })

  it('cancels out of edit mode without saving', async () => {
    const user = userEvent.setup()
    render(<AppointmentTypeConfigForm doctorProfileId={DOCTOR_PROFILE_ID} />)

    await screen.findByText('Gauresh Kumar')
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const editForm = within(screen.getByRole('form', { name: 'Edit Gauresh Kumar' }))
    await editForm.findByLabelText('Appointment type name')
    await user.click(editForm.getByRole('button', { name: 'Cancel' }))

    expect(screen.queryByRole('form', { name: 'Edit Gauresh Kumar' })).not.toBeInTheDocument()
    expect(mockedRename).not.toHaveBeenCalled()
  })

  it('shows an error message when the rename request fails', async () => {
    const user = userEvent.setup()
    mockedRename.mockRejectedValueOnce(new AppointmentTypeApiError({ error: 'FORBIDDEN' }))

    render(<AppointmentTypeConfigForm doctorProfileId={DOCTOR_PROFILE_ID} />)

    await screen.findByText('Gauresh Kumar')
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const editForm = within(screen.getByRole('form', { name: 'Edit Gauresh Kumar' }))
    await user.click(editForm.getByRole('button', { name: 'Save' }))

    expect(await editForm.findByRole('alert')).toHaveTextContent(/only this doctor/i)
  })
})
