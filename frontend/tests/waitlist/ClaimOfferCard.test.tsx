import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ClaimOfferCard } from '../../src/features/waitlist/ClaimOfferCard'
import { claimOffer, declineOffer, WaitlistClaimApiError } from '../../src/features/waitlist/api'
import { listPatientAppointmentTypes } from '../../src/features/appointment-types/api'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/waitlist/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/waitlist/api')>(
    '../../src/features/waitlist/api',
  )
  return {
    ...actual,
    claimOffer: vi.fn(),
    declineOffer: vi.fn(),
  }
})

vi.mock('../../src/features/appointment-types/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
    '../../src/features/appointment-types/api',
  )
  return {
    ...actual,
    listPatientAppointmentTypes: vi.fn(),
  }
})

const mockedClaimOffer = vi.mocked(claimOffer)
const mockedDeclineOffer = vi.mocked(declineOffer)
const mockedListPatientAppointmentTypes = vi.mocked(listPatientAppointmentTypes)

const ENTRY_ID = 'entry-1'
const DOCTOR_ID = 'doctor-1'

describe('ClaimOfferCard', () => {
  beforeEach(() => {
    mockedClaimOffer.mockReset()
    mockedDeclineOffer.mockReset()
    mockedListPatientAppointmentTypes.mockReset()
    mockedListPatientAppointmentTypes.mockResolvedValue([
      { id: 'apt-1', doctorProfileId: DOCTOR_ID, name: 'General Consultation', feeOverride: 500 },
      { id: 'apt-2', doctorProfileId: DOCTOR_ID, name: 'Follow-up', feeOverride: 300 },
    ])
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'patient-1', email: 'patient@example.com' })
  })

  // _diagnostics [HIGH] - [WAITLIST_CLAIM] - [RAW_ID_ENTRY]: the raw free-text "Appointment Type
  // ID" field is gone - a patient picks from the matched doctor's actual appointment types.
  it('claims the offer for an appointment type picked from the matched doctor', async () => {
    const user = userEvent.setup()
    mockedClaimOffer.mockResolvedValueOnce({
      id: 'booking-1',
      slotId: 'slot-1',
      patientId: 'patient-record-1',
      appointmentTypeId: 'apt-1',
      lockedFee: 300,
      paymentStatus: 'PENDING',
      status: 'ACTIVE',
      createdAt: '2026-09-04T10:00:00Z',
    })

    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={DOCTOR_ID} />)

    await user.type(screen.getByLabelText(/your name/i), 'Claimant')
    await screen.findByRole('option', { name: 'General Consultation' })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'apt-1')
    await user.click(screen.getByRole('button', { name: /claim slot/i }))

    expect(await screen.findByText(/slot claimed/i)).toBeInTheDocument()
    expect(screen.getByText(/payment pending/i)).toBeInTheDocument()
    expect(mockedClaimOffer).toHaveBeenCalledWith(
      ENTRY_ID,
      { appointmentTypeId: 'apt-1', patientName: 'Claimant' },
      'a.jwt.token',
    )
  })

  // patient-booking-visual-polish: fee preview parity with BookSlotForm/QueueBookSlotForm's own
  // appointment-type pickers.
  it('shows the fee for the selected appointment type', async () => {
    const user = userEvent.setup()
    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={DOCTOR_ID} />)

    await screen.findByRole('option', { name: 'Follow-up' })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'apt-2')

    expect(await screen.findByText('₹300.00')).toBeInTheDocument()
  })

  it('declines the offer', async () => {
    const user = userEvent.setup()
    mockedDeclineOffer.mockResolvedValueOnce({
      id: ENTRY_ID,
      clinicId: 'clinic-1',
      doctorProfileId: 'doctor-1',
      specialization: null,
      status: 'EXPIRED',
      joinedAt: '2026-09-04T09:00:00Z',
      offeredAt: null,
      offerExpiresAt: null,
      offeredDoctorProfileId: null,
    })

    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={DOCTOR_ID} />)

    await user.click(screen.getByRole('button', { name: /decline/i }))

    expect(await screen.findByText(/offer declined/i)).toBeInTheDocument()
    expect(mockedDeclineOffer).toHaveBeenCalledWith(ENTRY_ID, 'a.jwt.token')
  })

  it('shows the WAITLIST_OFFER_NOT_CLAIMABLE error message', async () => {
    const user = userEvent.setup()
    mockedClaimOffer.mockRejectedValueOnce(new WaitlistClaimApiError({ error: 'WAITLIST_OFFER_NOT_CLAIMABLE' }))

    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={DOCTOR_ID} />)

    await user.type(screen.getByLabelText(/your name/i), 'Claimant')
    await screen.findByRole('option', { name: 'General Consultation' })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'apt-1')
    await user.click(screen.getByRole('button', { name: /claim slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/no longer available to claim/i)
  })

  it('shows the SLOT_ALREADY_BOOKED error message', async () => {
    const user = userEvent.setup()
    mockedClaimOffer.mockRejectedValueOnce(new WaitlistClaimApiError({ error: 'SLOT_ALREADY_BOOKED' }))

    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={DOCTOR_ID} />)

    await user.type(screen.getByLabelText(/your name/i), 'Claimant')
    await screen.findByRole('option', { name: 'General Consultation' })
    await user.selectOptions(screen.getByLabelText(/appointment type/i), 'apt-1')
    await user.click(screen.getByRole('button', { name: /claim slot/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/just booked by someone else/i)
  })

  it('shows a fallback message when the matched doctor is unknown', () => {
    render(<ClaimOfferCard entryId={ENTRY_ID} offeredDoctorProfileId={null} />)

    expect(screen.getByText(/could not determine the doctor for this offer/i)).toBeInTheDocument()
  })
})
