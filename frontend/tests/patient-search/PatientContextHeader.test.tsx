import { render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { PatientContextHeader } from '../../src/features/patient-search/PatientContextHeader'
import { getPatient, PatientSearchApiError, type PatientSearchResult } from '../../src/features/patient-search/api'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return { ...actual, getPatient: vi.fn() }
})

const mockedGetPatient = vi.mocked(getPatient)

const CLINIC_ID = 'clinic-1'
const PATIENT_ID = 'patient-1'

const PATIENT: PatientSearchResult = { patientId: PATIENT_ID, name: 'Asha Rao', phone: '9876543210' }

describe('PatientContextHeader', () => {
  beforeEach(() => {
    mockedGetPatient.mockReset()
    storeStaffSession({ token: 'a.jwt.token', accountId: 'acct-1', email: 'ops@example.com' })
  })

  it("shows the patient's name and phone, and a link back to patient search", async () => {
    mockedGetPatient.mockResolvedValueOnce(PATIENT)
    render(
      <MemoryRouter>
        <PatientContextHeader clinicId={CLINIC_ID} patientId={PATIENT_ID} />
      </MemoryRouter>,
    )

    expect(await screen.findByRole('heading', { name: 'Asha Rao' })).toBeInTheDocument()
    expect(screen.getByText('9876543210')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /back to patient search/i })).toHaveAttribute(
      'href',
      '/staff/clinics/clinic-1/patients/search',
    )
  })

  it('renders nothing (no redundant error banner) when the lookup fails', async () => {
    mockedGetPatient.mockRejectedValueOnce(new PatientSearchApiError(404))
    const { container } = render(
      <MemoryRouter>
        <PatientContextHeader clinicId={CLINIC_ID} patientId={PATIENT_ID} />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(container).toBeEmptyDOMElement()
    })
  })
})
