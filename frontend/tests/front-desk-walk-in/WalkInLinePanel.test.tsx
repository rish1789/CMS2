import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { WalkInLinePanel } from '../../src/features/front-desk-walk-in/WalkInLinePanel'
import { getDaySheet, type SessionDaySheet, type SlotDetail } from '../../src/features/day-sheet/api'
import { completeSlot, markSlotAppeared } from '../../src/features/session-delay/api'
import { cancelBookingAsStaff } from '../../src/features/booking-cancellation/api'

vi.mock('../../src/features/day-sheet/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/day-sheet/api')>('../../src/features/day-sheet/api')
  return { ...actual, getDaySheet: vi.fn() }
})
vi.mock('../../src/features/session-delay/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/session-delay/api')>(
    '../../src/features/session-delay/api',
  )
  return { ...actual, markSlotAppeared: vi.fn(), completeSlot: vi.fn() }
})
vi.mock('../../src/features/booking-cancellation/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/booking-cancellation/api')>(
    '../../src/features/booking-cancellation/api',
  )
  return { ...actual, cancelBookingAsStaff: vi.fn() }
})

const mockedDaySheet = vi.mocked(getDaySheet)
const mockedAppeared = vi.mocked(markSlotAppeared)
const mockedComplete = vi.mocked(completeSlot)
const mockedCancel = vi.mocked(cancelBookingAsStaff)

function walkIn(token: number, status: SlotDetail['status'], name: string): SlotDetail {
  return {
    slotId: `slot-w${token}`,
    startTime: null,
    endTime: null,
    tokenNumber: token,
    status,
    appearedAt: null,
    completedAt: null,
    booking: {
      bookingId: `booking-w${token}`,
      patientId: `patient-${token}`,
      patientName: name,
      isWalkIn: true,
      visitReason: 'PAIN',
      visitReasonDetail: null,
    },
  }
}

const TIMED_BOOKED: SlotDetail = {
  slotId: 'slot-t1',
  startTime: '09:00:00',
  endTime: '09:15:00',
  tokenNumber: null,
  status: 'BOOKED',
  appearedAt: null,
  completedAt: null,
  booking: { bookingId: 'booking-t1', patientId: 'p-t1', patientName: 'Booked Patient', isWalkIn: false, visitReason: null, visitReasonDetail: null },
}

function daySheet(slots: SlotDetail[], mode: SessionDaySheet['mode'] = 'FIXED_TIME'): SessionDaySheet {
  return { sessionId: 'session-1', doctorProfileId: 'doctor-1', doctorName: 'Dr. Rao', sessionDate: '2026-09-24', mode, slots }
}

function renderPanel(onChanged = vi.fn(), mode: SessionDaySheet['mode'] = 'FIXED_TIME') {
  render(
    <WalkInLinePanel
      clinicId="clinic-1"
      token="staff-jwt"
      sessionId="session-1"
      mode={mode}
      doctorName="Dr. Rao"
      refreshKey={0}
      onChanged={onChanged}
    />,
  )
  return onChanged
}

// A token booked ahead (by the patient or by staff), not at the front desk.
function bookedToken(token: number, status: SlotDetail['status'], name: string): SlotDetail {
  const slot = walkIn(token, status, name)
  return { ...slot, slotId: `slot-q${token}`, booking: { ...slot.booking!, bookingId: `booking-q${token}`, isWalkIn: false, visitReason: null } }
}

beforeEach(() => {
  vi.clearAllMocks()
  mockedAppeared.mockResolvedValue({} as never)
  mockedComplete.mockResolvedValue({} as never)
  mockedCancel.mockResolvedValue({} as never)
})

describe('WalkInLinePanel (063-front-desk-walk-in US2)', () => {
  it('lists waiting walk-ins in arrival order with the first marked next, and says the doctor is free', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([TIMED_BOOKED, walkIn(2, 'BOOKED', 'Ravi Kumar'), walkIn(1, 'BOOKED', 'Asha Rao')]))
    renderPanel()

    const items = await screen.findAllByRole('listitem')
    expect(items.map((i) => within(i).getByText(/^W\d+$/).textContent)).toEqual(['W1', 'W2'])
    expect(within(items[0]).getByText(/next/i)).toBeInTheDocument()
    expect(screen.getByText(/doctor free now/i)).toBeInTheDocument()
    expect(screen.queryByText('Booked Patient')).not.toBeInTheDocument()
  })

  it('says the doctor is busy while anyone in the session is in with the doctor, but still allows Send in', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([{ ...TIMED_BOOKED, status: 'APPEARED' }, walkIn(1, 'BOOKED', 'Asha Rao')]))
    renderPanel()

    expect(await screen.findByText(/doctor busy/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /send in asha rao/i })).toBeEnabled()
  })

  it('sends a walk-in in through the existing appeared action and refreshes', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([walkIn(1, 'BOOKED', 'Asha Rao')]))
    const user = userEvent.setup()
    const onChanged = renderPanel()

    await user.click(await screen.findByRole('button', { name: /send in asha rao/i }))

    expect(mockedAppeared).toHaveBeenCalledWith('clinic-1', 'slot-w1', 'staff-jwt')
    expect(onChanged).toHaveBeenCalled()
    expect(mockedDaySheet).toHaveBeenCalledTimes(2)
  })

  it('completes the walk-in who is in with the doctor through the existing complete action', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([walkIn(1, 'APPEARED', 'Asha Rao')]))
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByRole('button', { name: /complete asha rao/i }))

    expect(mockedComplete).toHaveBeenCalledWith('clinic-1', 'slot-w1', 'staff-jwt')
  })

  it('removes a waiting walk-in only after confirming, through the existing staff cancel', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([walkIn(1, 'BOOKED', 'Asha Rao')]))
    const user = userEvent.setup()
    renderPanel()

    await user.click(await screen.findByRole('button', { name: /remove asha rao/i }))
    expect(mockedCancel).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: /yes, remove/i }))

    expect(mockedCancel).toHaveBeenCalledWith('clinic-1', 'booking-w1', 'staff-jwt')
  })
})

// 064-queue-send-in-complete (US3, FR-007, tasks.md T016): in a Queue session every waiting token is
// in the line - booked ahead or walked in - with the same Send in / Complete / Remove actions.
describe('WalkInLinePanel - Queue session (064-queue-send-in-complete)', () => {
  it('lists every waiting token in number order, labelled by token, with walk-ins badged', async () => {
    mockedDaySheet.mockResolvedValue(
      daySheet([bookedToken(3, 'BOOKED', 'Meera Das'), walkIn(2, 'BOOKED', 'Asha Rao'), bookedToken(1, 'COMPLETED', 'Seen Already')], 'QUEUE'),
    )
    renderPanel(vi.fn(), 'QUEUE')

    expect(await screen.findByRole('region', { name: /dr\. rao’s waiting line/i })).toBeInTheDocument()
    const items = await screen.findAllByRole('listitem')
    expect(items.map((i) => within(i).getByText(/^Token \d+$/).textContent)).toEqual(['Token 2', 'Token 3'])
    expect(within(items[0]).getByText('Walk-in')).toBeInTheDocument()
    expect(within(items[0]).getByText(/next/i)).toBeInTheDocument()
    expect(within(items[1]).queryByText('Walk-in')).not.toBeInTheDocument()
    expect(screen.queryByText('Seen Already')).not.toBeInTheDocument()
  })

  it('sends in, completes and removes a booked-ahead token through the existing actions', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([bookedToken(1, 'APPEARED', 'In Now'), bookedToken(2, 'BOOKED', 'Meera Das')], 'QUEUE'))
    const user = userEvent.setup()
    renderPanel(vi.fn(), 'QUEUE')

    expect(await screen.findByText(/doctor busy/i)).toBeInTheDocument()
    expect(screen.getByText('Token 1 · In Now')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: /complete in now/i }))
    expect(mockedComplete).toHaveBeenCalledWith('clinic-1', 'slot-q1', 'staff-jwt')

    await user.click(await screen.findByRole('button', { name: /send in meera das/i }))
    expect(mockedAppeared).toHaveBeenCalledWith('clinic-1', 'slot-q2', 'staff-jwt')

    await user.click(screen.getByRole('button', { name: /remove meera das/i }))
    await user.click(screen.getByRole('button', { name: /yes, remove/i }))
    expect(mockedCancel).toHaveBeenCalledWith('clinic-1', 'booking-q2', 'staff-jwt')
  })

  it('says no one is waiting when the queue is empty', async () => {
    mockedDaySheet.mockResolvedValue(daySheet([], 'QUEUE'))
    renderPanel(vi.fn(), 'QUEUE')

    expect(await screen.findByText(/no one waiting/i)).toBeInTheDocument()
  })
})
