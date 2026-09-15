import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes, useLocation, useParams } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { QueueSessionList } from '../../src/features/patient-booking/QueueSessionList'
import { listQueueSessions } from '../../src/features/patient-booking/api'
import { todayIsoDate } from '../../src/features/patient-booking/DateStrip'
import { storePatientSession } from '../../src/features/patient-account/token'

vi.mock('../../src/features/patient-booking/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-booking/api')>(
    '../../src/features/patient-booking/api',
  )
  return { ...actual, listQueueSessions: vi.fn() }
})

const mockedListQueueSessions = vi.mocked(listQueueSessions)

const CLINIC_ID = 'clinic-1'

const ONE_SESSION_RESULT = {
  sessions: [
    {
      sessionId: 'session-1',
      doctorProfileId: 'doc-1',
      doctorName: 'Dr. Asha Rao',
      sessionDate: todayIsoDate(),
      startTime: '16:00:00',
      endTime: '18:00:00',
      appointmentTypes: [{ id: 'type-1', doctorProfileId: 'doc-1', name: 'General Consultation', feeOverride: null }],
    },
  ],
  page: 0,
  pageSize: 15,
  totalCount: 1,
}

describe('QueueSessionList', () => {
  beforeEach(() => {
    mockedListQueueSessions.mockReset()
    storePatientSession({ token: 'a.jwt.token', patientAccountId: 'acct-1', email: 'patient@example.com' })
  })

  it('lists queue sessions with doctor, relative date, and time', async () => {
    mockedListQueueSessions.mockResolvedValueOnce(ONE_SESSION_RESULT)

    render(<QueueSessionList clinicId={CLINIC_ID} />, { wrapper: MemoryRouter })

    expect(await screen.findByText('Dr. Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Today')).toBeInTheDocument()
    expect(screen.getByText('16:00–18:00')).toBeInTheDocument()
  })

  it('shows an empty state when there are no upcoming queue sessions', async () => {
    mockedListQueueSessions.mockResolvedValueOnce({ sessions: [], page: 0, pageSize: 15, totalCount: 0 })

    render(<QueueSessionList clinicId={CLINIC_ID} />, { wrapper: MemoryRouter })

    expect(await screen.findByText(/no upcoming queue sessions/i)).toBeInTheDocument()
  })

  it('navigates to the queue-booking route with the appointment types carried in router state when Join is clicked', async () => {
    const user = userEvent.setup()
    mockedListQueueSessions.mockResolvedValueOnce(ONE_SESSION_RESULT)

    function DestinationProbe() {
      const { clinicId, sessionId } = useParams<{ clinicId: string; sessionId: string }>()
      const location = useLocation()
      const appointmentTypes = (location.state as { appointmentTypes?: unknown } | null)?.appointmentTypes
      return (
        <p>
          arrived at {clinicId}/{sessionId} with {JSON.stringify(appointmentTypes)}
        </p>
      )
    }

    render(
      <MemoryRouter initialEntries={[`/patient/clinics/${CLINIC_ID}/queue-sessions`]}>
        <Routes>
          <Route path="/patient/clinics/:clinicId/queue-sessions" element={<QueueSessionList clinicId={CLINIC_ID} />} />
          <Route path="/patient/clinics/:clinicId/sessions/:sessionId/queue-book" element={<DestinationProbe />} />
        </Routes>
      </MemoryRouter>,
    )

    await user.click(await screen.findByRole('button', { name: /join/i }))

    expect(await screen.findByText(`arrived at ${CLINIC_ID}/session-1 with ${JSON.stringify(ONE_SESSION_RESULT.sessions[0].appointmentTypes)}`)).toBeInTheDocument()
  })
})
