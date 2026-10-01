import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { FrontDeskWalkInPage } from '../../src/features/front-desk-walk-in/FrontDeskWalkInPage'
import { registerWalkIn } from '../../src/features/front-desk-walk-in/api'
import { getDaySheet, listSessions, type SessionSummary } from '../../src/features/day-sheet/api'
import { listDoctorBookingReadiness } from '../../src/features/doctor-picker/api'
import { getSessionLiveStatusAsStaff } from '../../src/features/session-delay/api'
import { searchPatients } from '../../src/features/patient-search/api'
import { listAppointmentTypes } from '../../src/features/appointment-types/api'
import { ApiError } from '../../src/lib/apiClient'
import { storeStaffSession } from '../../src/features/staff-login/token'

vi.mock('../../src/features/front-desk-walk-in/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/front-desk-walk-in/api')>(
    '../../src/features/front-desk-walk-in/api',
  )
  return { ...actual, registerWalkIn: vi.fn() }
})
vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>('../../src/features/day-sheet/api')
  return { ...actual, listSessions: vi.fn(), getDaySheet: vi.fn() }
})
vi.mock('../../src/features/doctor-picker/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/doctor-picker/api')>(
    '../../src/features/doctor-picker/api',
  )
  return { ...actual, listDoctorBookingReadiness: vi.fn() }
})
vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return { ...actual, getSessionLiveStatusAsStaff: vi.fn() }
})
vi.mock('../../src/features/patient-search/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/patient-search/api')>(
    '../../src/features/patient-search/api',
  )
  return { ...actual, searchPatients: vi.fn() }
})
vi.mock('../../src/features/appointment-types/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/appointment-types/api')>(
    '../../src/features/appointment-types/api',
  )
  return { ...actual, listAppointmentTypes: vi.fn() }
})

const mockedRegister = vi.mocked(registerWalkIn)
const mockedListSessions = vi.mocked(listSessions)
const mockedDaySheet = vi.mocked(getDaySheet)
const mockedReadiness = vi.mocked(listDoctorBookingReadiness)
const mockedLiveStatus = vi.mocked(getSessionLiveStatusAsStaff)
const mockedSearch = vi.mocked(searchPatients)
const mockedTypes = vi.mocked(listAppointmentTypes)

// The fixtures below are HH:MM times relative to "now" on today's date, so near midnight they
// wrapped (e.g. 3 hours before 01:44 became 22:44, i.e. later today) and an ended session read as
// still running. Pin only Date to today's midday before any fixture is built - timers stay real,
// so user-event keeps working - and restore it after this file.
vi.useFakeTimers({ toFake: ['Date'] })
vi.setSystemTime(new Date(new Date().setHours(12, 0, 0, 0)))
afterAll(() => {
  vi.useRealTimers()
})

function hoursFromNow(hours: number): string {
  const d = new Date(Date.now() + hours * 3600_000)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:00`
}

const today = new Date()
const TODAY = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`

const FIXED: SessionSummary = {
  sessionId: 'session-fixed',
  doctorProfileId: 'doctor-rao',
  doctorName: 'Dr. Rao',
  sessionDate: TODAY,
  startTime: hoursFromNow(-1),
  endTime: hoursFromNow(2),
  mode: 'FIXED_TIME',
  bookedSlotCount: 5,
  totalSlotCount: 12,
  walkInsWaiting: 2,
  inWithDoctor: false,
}
const QUEUE: SessionSummary = {
  ...FIXED,
  sessionId: 'session-queue',
  doctorProfileId: 'doctor-mehta',
  doctorName: 'Dr. Mehta',
  mode: 'QUEUE',
  walkInsWaiting: 3,
  inWithDoctor: true,
}
const NOT_READY: SessionSummary = { ...FIXED, sessionId: 'session-not-ready', doctorProfileId: 'doctor-new', doctorName: 'Dr. New' }
const ENDED: SessionSummary = {
  ...FIXED,
  sessionId: 'session-ended',
  doctorProfileId: 'doctor-early',
  doctorName: 'Dr. Early',
  startTime: hoursFromNow(-5),
  endTime: hoursFromNow(-3),
  walkInsWaiting: 0,
  inWithDoctor: false,
}

function renderPage(initialEntry = '/staff/clinics/clinic-1/walk-in') {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <FrontDeskWalkInPage clinicId="clinic-1" />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionStorage.clear()
  storeStaffSession({ token: 'staff-jwt', accountId: 'account-1', email: 'desk@clinic.example' })
  mockedDaySheet.mockResolvedValue({ sessionId: 'session-fixed', doctorProfileId: 'doctor-rao', doctorName: 'Dr. Rao', sessionDate: TODAY, mode: 'FIXED_TIME', slots: [] })
  mockedListSessions.mockResolvedValue({ sessions: [FIXED, QUEUE, NOT_READY, ENDED], doctors: [], page: 0, pageSize: 50, totalCount: 4 })
  mockedReadiness.mockResolvedValue([
    { doctorProfileId: 'doctor-rao', hasAppointmentTypes: true, hasDefaultFee: true, hasAppointmentTypeMissingFeeOverride: false, bookingReady: true },
    { doctorProfileId: 'doctor-mehta', hasAppointmentTypes: true, hasDefaultFee: true, hasAppointmentTypeMissingFeeOverride: false, bookingReady: true },
    { doctorProfileId: 'doctor-new', hasAppointmentTypes: false, hasDefaultFee: false, hasAppointmentTypeMissingFeeOverride: false, bookingReady: false },
    { doctorProfileId: 'doctor-early', hasAppointmentTypes: true, hasDefaultFee: true, hasAppointmentTypeMissingFeeOverride: false, bookingReady: true },
  ])
  mockedLiveStatus.mockResolvedValue({
    sessionId: 'session-fixed',
    applicable: true,
    status: 'ON_TIME',
    currentPatientOrdinal: 3,
    expectedPatientOrdinal: 3,
    deviationMinutes: null,
    firstSlotTime: '09:00:00',
    operationalDay: TODAY,
  })
  mockedSearch.mockResolvedValue({ patients: [], page: 0, pageSize: 8, totalCount: 0 })
  mockedTypes.mockResolvedValue([{ id: 'type-1', doctorProfileId: 'doctor-rao', name: 'Consultation', feeOverride: null }])
})

async function fillNewPatientAndReason(user: ReturnType<typeof userEvent.setup>, reason = 'Pain') {
  await user.click(screen.getByRole('radio', { name: /new patient/i }))
  await user.type(screen.getByLabelText(/^name/i), 'Asha Rao')
  await user.selectOptions(screen.getByLabelText(/reason for visit/i), reason)
}

async function chooseSessionAndType(user: ReturnType<typeof userEvent.setup>, doctor = /dr\. rao/i) {
  await user.click(await screen.findByRole('radio', { name: doctor }))
  await user.selectOptions(await screen.findByLabelText('Appointment type'), 'Consultation')
}

describe('FrontDeskWalkInPage (063-front-desk-walk-in US1)', () => {
  it('lists today’s running sessions with live status, load and the doctor free hint, hiding sessions that are over', async () => {
    renderPage()

    const rao = await screen.findByRole('radio', { name: /dr\. rao/i })
    const card = rao.closest('label') as HTMLElement
    expect(within(card).getByText('On time')).toBeInTheDocument()
    expect(within(card).getByText(/5 of 12 booked/i)).toBeInTheDocument()
    expect(within(card).getByText(/2 walk-ins waiting/i)).toBeInTheDocument()
    expect(within(card).getByText(/doctor free now/i)).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /dr\. mehta/i })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /dr\. early/i })).not.toBeInTheDocument()
  })

  // 065-phase1-stabilization: a cancelled session takes no walk-ins, so it isn't offered.
  it('does not offer a cancelled session', async () => {
    mockedListSessions.mockResolvedValueOnce({
      sessions: [FIXED, { ...QUEUE, cancelled: true }],
      doctors: [],
      page: 0,
      pageSize: 50,
      totalCount: 2,
    })
    renderPage()

    expect(await screen.findByRole('radio', { name: /dr\. rao/i })).toBeInTheDocument()
    expect(screen.queryByRole('radio', { name: /dr\. mehta/i })).not.toBeInTheDocument()
  })

  // 064-queue-send-in-complete (US3, FR-007, tasks.md T016): a Queue session gets the same waiting
  // count, doctor hint and waiting-line panel as a Fixed-Time one.
  it('shows a Queue session’s waiting tokens and doctor hint, and its waiting line once chosen', async () => {
    const user = userEvent.setup()
    renderPage()

    const mehta = await screen.findByRole('radio', { name: /dr\. mehta/i })
    const card = mehta.closest('label') as HTMLElement
    expect(within(card).getByText(/^3 waiting$/i)).toBeInTheDocument()
    expect(within(card).getByText(/doctor busy/i)).toBeInTheDocument()
    expect(within(card).queryByText(/booked/i)).not.toBeInTheDocument()

    await user.click(mehta)

    expect(await screen.findByRole('region', { name: /dr\. mehta’s waiting line/i })).toBeInTheDocument()
  })

  it('shows a doctor whose booking setup is incomplete as unavailable, with the reason', async () => {
    renderPage()

    const notReady = await screen.findByRole('radio', { name: /dr\. new/i })
    expect(notReady).toBeDisabled()
    expect(within(notReady.closest('label') as HTMLElement).getByText(/booking setup incomplete/i)).toBeInTheDocument()
  })

  it('requires text when the visit reason is Other', async () => {
    const user = userEvent.setup()
    renderPage()
    await fillNewPatientAndReason(user, 'Other')
    await chooseSessionAndType(user)

    await user.click(screen.getByRole('button', { name: /register walk-in/i }))

    expect(await screen.findByText(/describe the reason for the visit/i)).toBeInTheDocument()
    expect(mockedRegister).not.toHaveBeenCalled()
  })

  it('offers the existing patient when a new patient’s phone already matches one', async () => {
    mockedSearch.mockResolvedValue({
      patients: [{ patientId: 'patient-9', name: 'Asha Rao', phone: '9876543210', anonymizedAt: null, patientAccountId: null }],
      page: 0,
      pageSize: 8,
      totalCount: 1,
    } as never)
    const user = userEvent.setup()
    renderPage()
    await user.click(screen.getByRole('radio', { name: /new patient/i }))
    await user.type(screen.getByLabelText(/phone/i), '9876543210')

    await user.click(await screen.findByRole('button', { name: /use asha rao/i }))

    expect(screen.getByText(/selected patient/i)).toBeInTheDocument()
    expect(screen.getByText('Asha Rao')).toBeInTheDocument()
  })

  it('registers a Fixed-Time walk-in and shows the W-number, position and fee', async () => {
    mockedRegister.mockResolvedValue({
      bookingId: 'booking-1',
      slotId: 'slot-1',
      sessionId: 'session-fixed',
      mode: 'FIXED_TIME',
      tokenNumber: 3,
      walkInPosition: 3,
      patientId: 'patient-1',
      patientName: 'Asha Rao',
      doctorName: 'Dr. Rao',
      appointmentTypeId: 'type-1',
      lockedFee: 450,
      visitReason: 'PAIN',
      visitReasonDetail: null,
    })
    const user = userEvent.setup()
    renderPage()
    await fillNewPatientAndReason(user)
    await chooseSessionAndType(user)

    await user.click(screen.getByRole('button', { name: /register walk-in/i }))

    expect(await screen.findByText('W3')).toBeInTheDocument()
    expect(screen.getByText(/position 3 in dr\. rao’s walk-in line/i)).toBeInTheDocument()
    expect(screen.getByText(/₹450\.00/)).toBeInTheDocument()
    expect(mockedRegister).toHaveBeenCalledWith(
      'clinic-1',
      expect.objectContaining({ sessionId: 'session-fixed', patientName: 'Asha Rao', visitReason: 'PAIN', confirmDuplicate: false }),
      'staff-jwt',
    )
  })

  it('shows only the token for a Queue walk-in', async () => {
    mockedTypes.mockResolvedValue([{ id: 'type-1', doctorProfileId: 'doctor-mehta', name: 'Consultation', feeOverride: null }])
    mockedRegister.mockResolvedValue({
      bookingId: 'booking-2',
      slotId: 'slot-2',
      sessionId: 'session-queue',
      mode: 'QUEUE',
      tokenNumber: 7,
      walkInPosition: null,
      patientId: 'patient-1',
      patientName: 'Asha Rao',
      doctorName: 'Dr. Mehta',
      appointmentTypeId: 'type-1',
      lockedFee: 300,
      visitReason: 'PAIN',
      visitReasonDetail: null,
    })
    const user = userEvent.setup()
    renderPage()
    await fillNewPatientAndReason(user)
    await chooseSessionAndType(user, /dr\. mehta/i)

    await user.click(screen.getByRole('button', { name: /register walk-in/i }))

    expect(await screen.findByText(/token 7/i)).toBeInTheDocument()
    expect(screen.queryByText(/position/i)).not.toBeInTheDocument()
  })

  it('asks for confirmation on a duplicate and resubmits with confirmDuplicate', async () => {
    mockedRegister
      .mockRejectedValueOnce(
        new ApiError(409, 'This patient is already in this session today. Register them again?', { error: 'DUPLICATE_WALK_IN' }),
      )
      .mockResolvedValueOnce({
        bookingId: 'booking-3',
        slotId: 'slot-3',
        sessionId: 'session-fixed',
        mode: 'FIXED_TIME',
        tokenNumber: 4,
        walkInPosition: 4,
        patientId: 'patient-1',
        patientName: 'Asha Rao',
        doctorName: 'Dr. Rao',
        appointmentTypeId: 'type-1',
        lockedFee: 450,
        visitReason: 'PAIN',
        visitReasonDetail: null,
      })
    const user = userEvent.setup()
    renderPage()
    await fillNewPatientAndReason(user)
    await chooseSessionAndType(user)

    await user.click(screen.getByRole('button', { name: /register walk-in/i }))
    await user.click(await screen.findByRole('button', { name: /register again/i }))

    expect(await screen.findByText('W4')).toBeInTheDocument()
    expect(mockedRegister).toHaveBeenLastCalledWith('clinic-1', expect.objectContaining({ confirmDuplicate: true }), 'staff-jwt')
  })

  it('pre-selects the session from the URL', async () => {
    renderPage('/staff/clinics/clinic-1/walk-in?sessionId=session-queue')

    await waitFor(() => expect(screen.getByRole('radio', { name: /dr\. mehta/i })).toBeChecked())
  })
})
