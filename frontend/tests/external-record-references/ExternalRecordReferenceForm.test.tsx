import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ExternalRecordReferenceForm } from '../../src/features/external-record-references/ExternalRecordReferenceForm'
import {
  createExternalRecordReference,
  listExternalRecordReferences,
  ExternalRecordReferenceApiError,
} from '../../src/features/external-record-references/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/external-record-references/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/external-record-references/api')>(
    '../../src/features/external-record-references/api',
  )
  return {
    ...actual,
    createExternalRecordReference: vi.fn(),
    listExternalRecordReferences: vi.fn(),
  }
})

const mockedCreate = vi.mocked(createExternalRecordReference)
const mockedList = vi.mocked(listExternalRecordReferences)

const CLINIC_ID = 'clinic-1'
const BOOKING_ID = 'booking-1'

describe('ExternalRecordReferenceForm', () => {
  beforeEach(() => {
    mockedCreate.mockReset()
    mockedList.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'doctor-1', email: 'doctor@example.com' })
  })

  it('creates a reference and lists it afterward', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([])
    mockedCreate.mockResolvedValueOnce({
      id: 'ref-1',
      bookingId: BOOKING_ID,
      doctorProfileId: 'doctor-profile-1',
      recordType: 'Lab result',
      sourceProvider: 'City Diagnostics',
      recordDate: '2026-08-20',
      summary: 'CBC normal.',
      createdAt: '2026-09-04T10:00:00Z',
    })

    render(<ExternalRecordReferenceForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.type(await screen.findByLabelText('Record type'), 'Lab result')
    await user.type(screen.getByLabelText('Source / provider'), 'City Diagnostics')
    await user.type(screen.getByLabelText('Record date'), '2026-08-20')
    await user.type(screen.getByLabelText('Summary'), 'CBC normal.')
    await user.click(screen.getByRole('button', { name: /save reference/i }))

    expect(await screen.findByText(/lab result — city diagnostics/i)).toBeInTheDocument()
    expect(mockedCreate).toHaveBeenCalledWith(
      CLINIC_ID,
      BOOKING_ID,
      { recordType: 'Lab result', sourceProvider: 'City Diagnostics', recordDate: '2026-08-20', summary: 'CBC normal.' },
      'a.jwt.token',
    )
  })

  it('shows existing references read-only on load', async () => {
    mockedList.mockResolvedValueOnce([
      {
        id: 'ref-1',
        bookingId: BOOKING_ID,
        doctorProfileId: 'doctor-profile-1',
        recordType: 'Imaging report',
        sourceProvider: 'Metro Radiology',
        recordDate: '2026-08-21',
        summary: 'Chest X-ray clear.',
        createdAt: '2026-09-04T10:00:00Z',
      },
    ])

    render(<ExternalRecordReferenceForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByText(/imaging report — metro radiology/i)).toBeInTheDocument()
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedList.mockResolvedValueOnce([])
    mockedCreate.mockRejectedValueOnce(new ExternalRecordReferenceApiError({ error: 'FORBIDDEN' }))

    render(<ExternalRecordReferenceForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    await user.type(await screen.findByLabelText('Record type'), 'X')
    await user.type(screen.getByLabelText('Source / provider'), 'X')
    await user.type(screen.getByLabelText('Record date'), '2026-08-20')
    await user.type(screen.getByLabelText('Summary'), 'X')
    await user.click(screen.getByRole('button', { name: /save reference/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only the treating doctor/i)
  })

  // staff-console-audit-2026-09-10 P2: used to show this message as a banner above a form that
  // was still live and submittable, for a booking that doesn't exist.
  it('blocks the form entirely when the booking itself is not found', async () => {
    mockedList.mockRejectedValueOnce(new ExternalRecordReferenceApiError({ error: 'BOOKING_NOT_FOUND' }))

    render(<ExternalRecordReferenceForm clinicId={CLINIC_ID} bookingId={BOOKING_ID} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not be found/i)
    expect(screen.queryByLabelText('Record type')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /save reference/i })).not.toBeInTheDocument()
  })
})
