import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PrescriptionForm } from '../../src/features/prescriptions/PrescriptionForm'
import { createPrescription, listPrescriptions, PrescriptionApiError } from '../../src/features/prescriptions/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/prescriptions/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/prescriptions/api')>(
    '../../src/features/prescriptions/api',
  )
  return {
    ...actual,
    createPrescription: vi.fn(),
    listPrescriptions: vi.fn(),
  }
})

const mockedCreate = vi.mocked(createPrescription)
const mockedList = vi.mocked(listPrescriptions)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

// staff-console-audit-2026-09-10 P1: fields are now visibly labelled (not placeholder-only),
// scoped per item via the fieldset/legend pair ("Item 1", "Item 2", ...) rather than by
// numbering every individual field label, which would just repeat the fieldset's own context.
async function itemFieldset(name: string) {
  return within(await screen.findByRole('group', { name }))
}

describe('PrescriptionForm', () => {
  beforeEach(() => {
    mockedCreate.mockReset()
    mockedList.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'doctor-1', email: 'doctor@example.com' })
  })

  it('creates a prescription with one item and lists it afterward', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([])
    mockedCreate.mockResolvedValueOnce({
      id: 'prescription-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-profile-1',
      createdAt: '2026-09-04T10:00:00Z',
      items: [
        {
          id: 'item-1',
          medicationName: 'Amoxicillin',
          dosage: '500mg',
          frequency: '3x daily',
          duration: '7 days',
          instructions: 'With food',
        },
      ],
    })

    render(<PrescriptionForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const item1 = await itemFieldset('Item 1')
    await user.type(item1.getByLabelText('Medication name'), 'Amoxicillin')
    await user.type(item1.getByLabelText('Dosage'), '500mg')
    await user.type(item1.getByLabelText('Frequency'), '3x daily')
    await user.type(item1.getByLabelText('Duration'), '7 days')
    await user.click(screen.getByRole('button', { name: /save prescription/i }))

    expect(await screen.findByText(/amoxicillin — 500mg, 3x daily, 7 days/i)).toBeInTheDocument()
    expect(mockedCreate).toHaveBeenCalledWith(
      CLINIC_ID,
      BOOKING_ID,
      [{ medicationName: 'Amoxicillin', dosage: '500mg', frequency: '3x daily', duration: '7 days', instructions: '' }],
      'a.jwt.token',
    )
  })

  it('shows existing prescriptions read-only on load', async () => {
    mockedList.mockResolvedValueOnce([
      {
        id: 'prescription-1',
        bookingId: BOOKING_ID,
        doctorProfileId: 'doctor-profile-1',
        createdAt: '2026-09-04T10:00:00Z',
        items: [
          { id: 'item-1', medicationName: 'Ibuprofen', dosage: '200mg', frequency: '2x daily', duration: '3 days', instructions: null },
        ],
      },
    ])

    render(<PrescriptionForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/ibuprofen — 200mg, 2x daily, 3 days/i)).toBeInTheDocument()
  })

  it('adds a second item row when "Add another item" is clicked', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([])

    render(<PrescriptionForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await screen.findByRole('group', { name: 'Item 1' })
    await user.click(screen.getByRole('button', { name: /add another item/i }))

    expect(screen.getByRole('group', { name: 'Item 2' })).toBeInTheDocument()
  })

  it('shows the PRESCRIPTION_ITEM_REQUIRED error message', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([])
    mockedCreate.mockRejectedValueOnce(new PrescriptionApiError({ error: 'PRESCRIPTION_ITEM_REQUIRED' }))

    render(<PrescriptionForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    const item1 = await itemFieldset('Item 1')
    await user.type(item1.getByLabelText('Medication name'), 'X')
    await user.type(item1.getByLabelText('Dosage'), '1')
    await user.type(item1.getByLabelText('Frequency'), '1')
    await user.type(item1.getByLabelText('Duration'), '1')
    await user.click(screen.getByRole('button', { name: /save prescription/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/add at least one medication item/i)
  })

  // staff-console-audit-2026-09-10 P2: used to show this message as a banner above a form that
  // was still live and submittable, for a booking that doesn't exist.
  it('blocks the form entirely when the booking itself is not found', async () => {
    mockedList.mockRejectedValueOnce(new PrescriptionApiError({ error: 'BOOKING_NOT_FOUND' }))

    render(<PrescriptionForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i)
    expect(screen.queryByRole('group', { name: 'Item 1' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save prescription/i })).not.toBeInTheDocument()
  })
})
