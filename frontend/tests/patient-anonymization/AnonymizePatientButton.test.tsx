import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AnonymizePatientButton } from '../../src/features/patient-anonymization/AnonymizePatientButton'
import { anonymizePatient, PatientAnonymizationApiError } from '../../src/features/patient-anonymization/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/patient-anonymization/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-anonymization/api')>(
    '../../src/features/patient-anonymization/api',
  )
  return {
    ...actual,
    anonymizePatient: vi.fn(),
  }
})

const mockedAnonymize = vi.mocked(anonymizePatient)

const CLINIC_ID = 'clinic-1'
const PATIENT_ID = 'patient-1'

describe('AnonymizePatientButton', () => {
  beforeEach(() => {
    mockedAnonymize.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'staff-1', email: 'staff@example.com' })
  })

  it('anonymizes the patient after confirmation', async () => {
    const user = userEvent.setup()
    mockedAnonymize.mockResolvedValueOnce({
      patientId: PATIENT_ID,
      anonymized: true,
      anonymizedAt: '2026-09-04T10:00:00Z',
    })

    render(<AnonymizePatientButton clinicId={CLINIC_ID} patientId={PATIENT_ID} />)

    await user.click(screen.getByRole('button', { name: /anonymize patient/i }))
    await user.click(screen.getByRole('button', { name: /confirm/i }))

    expect(await screen.findByText(/patient anonymized/i)).toBeInTheDocument()
    expect(mockedAnonymize).toHaveBeenCalledWith(CLINIC_ID, PATIENT_ID, 'a.jwt.token')
  })

  it('cancels the confirmation without submitting', async () => {
    const user = userEvent.setup()

    render(<AnonymizePatientButton clinicId={CLINIC_ID} patientId={PATIENT_ID} />)

    await user.click(screen.getByRole('button', { name: /anonymize patient/i }))
    await user.click(screen.getByRole('button', { name: /^cancel$/i }))

    expect(screen.getByRole('button', { name: /anonymize patient/i })).toBeInTheDocument()
    expect(mockedAnonymize).not.toHaveBeenCalled()
  })

  it('shows the PATIENT_HAS_ACTIVE_FUTURE_BOOKING error message', async () => {
    const user = userEvent.setup()
    mockedAnonymize.mockRejectedValueOnce(new PatientAnonymizationApiError({ error: 'PATIENT_HAS_ACTIVE_FUTURE_BOOKING' }))

    render(<AnonymizePatientButton clinicId={CLINIC_ID} patientId={PATIENT_ID} />)

    await user.click(screen.getByRole('button', { name: /anonymize patient/i }))
    await user.click(screen.getByRole('button', { name: /confirm/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/active future booking/i)
  })

  it('shows the FORBIDDEN error message', async () => {
    const user = userEvent.setup()
    mockedAnonymize.mockRejectedValueOnce(new PatientAnonymizationApiError({ error: 'FORBIDDEN' }))

    render(<AnonymizePatientButton clinicId={CLINIC_ID} patientId={PATIENT_ID} />)

    await user.click(screen.getByRole('button', { name: /anonymize patient/i }))
    await user.click(screen.getByRole('button', { name: /confirm/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/only operations or clinicadmin/i)
  })
})
