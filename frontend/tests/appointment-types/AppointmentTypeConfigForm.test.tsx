import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AppointmentTypeConfigForm } from '../../src/features/appointment-types/AppointmentTypeConfigForm'
import {
  getClinicFees,
  removeClinicTypePrice,
  renameAppointmentType,
  setClinicDefaultFee,
  setClinicTypePrice,
  AppointmentTypeApiError,
  type ClinicDoctorFees,
} from '../../src/features/appointment-types/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/appointment-types/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
    '../../src/features/appointment-types/api',
  )
  return {
    ...actual,
    getClinicFees: vi.fn(),
    renameAppointmentType: vi.fn(),
    setClinicDefaultFee: vi.fn(),
    setClinicTypePrice: vi.fn(),
    removeClinicTypePrice: vi.fn(),
  }
})

const mockedGetFees = vi.mocked(getClinicFees)
const mockedRename = vi.mocked(renameAppointmentType)
const mockedSetDefault = vi.mocked(setClinicDefaultFee)
const mockedSetPrice = vi.mocked(setClinicTypePrice)
const mockedRemovePrice = vi.mocked(removeClinicTypePrice)

const CLINIC_ID = 'clinic-1'
const DOCTOR_PROFILE_ID = 'doctor-1'

function fees(
  name: string,
  { defaultFee = null, price = null }: { defaultFee?: number | null; price?: number | null } = {},
): ClinicDoctorFees {
  return {
    clinicId: CLINIC_ID,
    doctorProfileId: DOCTOR_PROFILE_ID,
    defaultFee,
    appointmentTypes: [{ appointmentTypeId: 'type-1', name, price, effectiveFee: price ?? defaultFee }],
  }
}

function renderForm(canEditPrices = true) {
  return render(
    <AppointmentTypeConfigForm clinicId={CLINIC_ID} doctorProfileId={DOCTOR_PROFILE_ID} canEditPrices={canEditPrices} />,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedGetFees.mockResolvedValue(fees('Gauresh Kumar'))
  storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'admin@example.com' })
})

// real-bug-fix 2026-09-17: covers the fix for "created an appointment type with the wrong name
// and had no way to correct it" - create/list previously had no update capability at all.
describe('AppointmentTypeConfigForm - editing an existing appointment type', () => {
  it('lets staff correct a mistyped name via the Edit action', async () => {
    const user = userEvent.setup()
    mockedRename.mockResolvedValueOnce({
      id: 'type-1',
      doctorProfileId: DOCTOR_PROFILE_ID,
      name: 'General Consultation',
      fee: null,
    })
    mockedGetFees.mockResolvedValueOnce(fees('Gauresh Kumar'))
    mockedGetFees.mockResolvedValueOnce(fees('General Consultation'))

    renderForm()

    await screen.findByText('Gauresh Kumar')
    await user.click(screen.getByRole('button', { name: 'Edit' }))

    const editForm = within(screen.getByRole('form', { name: 'Edit Gauresh Kumar' }))
    const nameInput = editForm.getByLabelText('Appointment type name')
    expect(nameInput).toHaveValue('Gauresh Kumar')
    await user.clear(nameInput)
    await user.type(nameInput, 'General Consultation')
    await user.click(editForm.getByRole('button', { name: 'Save' }))

    expect(mockedRename).toHaveBeenCalledWith(DOCTOR_PROFILE_ID, 'type-1', { name: 'General Consultation' }, 'a.jwt.token')
    expect(await screen.findByText('Appointment type updated.')).toBeInTheDocument()
    expect(await screen.findByText('General Consultation')).toBeInTheDocument()
  })

  it('cancels out of edit mode without saving', async () => {
    const user = userEvent.setup()
    renderForm()

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

    renderForm()

    await screen.findByText('Gauresh Kumar')
    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const editForm = within(screen.getByRole('form', { name: 'Edit Gauresh Kumar' }))
    await user.click(editForm.getByRole('button', { name: 'Save' }))

    expect(await editForm.findByRole('alert')).toHaveTextContent(/do not have permission/i)
  })
})

// 068-per-clinic-fees: prices are this clinic's own, and only its admin can change them.
describe('AppointmentTypeConfigForm - prices at this clinic', () => {
  it("loads this clinic's prices and flags a type with no price here", async () => {
    renderForm()

    expect(await screen.findByText('No price - not bookable here')).toBeInTheDocument()
    expect(screen.getByText('Not set')).toBeInTheDocument()
    expect(mockedGetFees).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_PROFILE_ID, 'a.jwt.token')
  })

  it('lets the clinic admin set the default fee for this clinic', async () => {
    const user = userEvent.setup()
    mockedSetDefault.mockResolvedValueOnce(fees('Gauresh Kumar', { defaultFee: 500 }))
    renderForm()

    await screen.findByText('Gauresh Kumar')
    await user.type(screen.getByLabelText('Amount'), '500')
    await user.click(screen.getByRole('button', { name: 'Set default fee' }))

    expect(mockedSetDefault).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_PROFILE_ID, 500, 'a.jwt.token')
    expect(await screen.findByText('Default fee updated.')).toBeInTheDocument()
    expect(screen.getByText('₹500.00')).toBeInTheDocument()
    expect(screen.getByText('Default fee (₹500.00)')).toBeInTheDocument()
  })

  it("lets the clinic admin set, then clear, a type's price at this clinic", async () => {
    const user = userEvent.setup()
    mockedGetFees.mockResolvedValueOnce(fees('Consultation', { defaultFee: 500 }))
    mockedSetPrice.mockResolvedValueOnce(fees('Consultation', { defaultFee: 500, price: 800 }))
    mockedRemovePrice.mockResolvedValueOnce(fees('Consultation', { defaultFee: 500 }))
    renderForm()

    await screen.findByText('Consultation')
    await user.click(screen.getByRole('button', { name: 'Set price for Consultation' }))
    const priceForm = within(screen.getByRole('form', { name: 'Price Consultation' }))
    await user.type(priceForm.getByLabelText('Price at this clinic'), '800')
    await user.click(priceForm.getByRole('button', { name: 'Save' }))

    expect(mockedSetPrice).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_PROFILE_ID, 'type-1', 800, 'a.jwt.token')
    expect(await screen.findByText('₹800.00')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Set price for Consultation' }))
    await user.click(screen.getByRole('button', { name: 'Use default fee' }))

    expect(mockedRemovePrice).toHaveBeenCalledWith(CLINIC_ID, DOCTOR_PROFILE_ID, 'type-1', 'a.jwt.token')
    expect(await screen.findByText('Default fee (₹500.00)')).toBeInTheDocument()
  })

  it("shows prices read-only to staff who are not this clinic's admin", async () => {
    mockedGetFees.mockResolvedValueOnce(fees('Consultation', { defaultFee: 500 }))
    renderForm(false)

    await screen.findByText('Consultation')
    expect(screen.getByText(/only this clinic's admin can change them/i)).toBeInTheDocument()
    expect(screen.queryByRole('form', { name: 'Set default fee' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Set price for Consultation' })).not.toBeInTheDocument()
    expect(screen.getByText('Default fee (₹500.00)')).toBeInTheDocument()
  })
})
