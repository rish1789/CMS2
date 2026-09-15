import { useEffect, useState } from 'react'
import { listOpenSlots, type OpenSlot, type BookingResponse } from './api'
import { BookSlotForm } from './BookSlotForm'
import { DateStrip, todayIsoDate } from './DateStrip'
import { loadPatientSession } from '../patient-account/token'
import { PaginationControls } from '../../components/PaginationControls'
import { IconBadge, DoctorIcon } from '../../components/adminIcons'
import { ListSkeleton } from '../../components/ListSkeleton'

const SLOTS_PAGE_SIZE = 15

export interface OpenSlotListProps {
  clinicId: string
  doctorId?: string
}

function formatDateHeading(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
}

// "10:15:00" -> "10:15" - the seconds are never meaningful to a patient picking a time.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

// "10:15:00"/"10:45:00" -> "30 min" - a slot's duration is a fixed schedule property (every
// slot within one doctor's day shares it), so trading the redundant "-10:45" for it turns each
// chip into two genuinely different pieces of information instead of one long number repeated.
function formatDuration(startTime: string, endTime: string): string {
  const [startHour, startMinute] = startTime.split(':').map(Number)
  const [endHour, endMinute] = endTime.split(':').map(Number)
  const minutes = endHour * 60 + endMinute - (startHour * 60 + startMinute)
  if (minutes % 60 === 0) return `${minutes / 60} hr`
  return `${minutes} min`
}

// Groups already-sorted (by sessionDate, then startTime) slots by doctor, preserving each
// doctor's first-seen order - so a multi-doctor day reads as "Dr. A's times, then Dr. B's",
// not one long list where names repeat down every single row.
function groupByDoctor(slots: OpenSlot[]): { doctorProfileId: string; doctorName: string; slots: OpenSlot[] }[] {
  const groups: { doctorProfileId: string; doctorName: string; slots: OpenSlot[] }[] = []
  for (const slot of slots) {
    const existing = groups.find((g) => g.doctorProfileId === slot.doctorProfileId)
    if (existing) {
      existing.slots.push(slot)
    } else {
      groups.push({ doctorProfileId: slot.doctorProfileId, doctorName: slot.doctorName, slots: [slot] })
    }
  }
  return groups
}

const PERIODS = ['Morning', 'Afternoon', 'Evening'] as const
type Period = (typeof PERIODS)[number]

function periodOf(startTime: string): Period {
  const hour = Number(startTime.slice(0, 2))
  if (hour < 12) return 'Morning'
  if (hour < 17) return 'Afternoon'
  return 'Evening'
}

// patient-booking-visual-polish: splits one doctor's (already time-sorted) slots into
// Morning/Afternoon/Evening bands - a wall of 13+ identical buttons is hard to scan, and this
// is the same grouping a patient already uses mentally ("can I get a morning appointment?").
// A single-period day skips the label entirely - it would just repeat what's already obvious.
function groupByPeriod(slots: OpenSlot[]): { period: Period; slots: OpenSlot[] }[] {
  const groups: { period: Period; slots: OpenSlot[] }[] = []
  for (const slot of slots) {
    const period = periodOf(slot.startTime)
    const existing = groups.find((g) => g.period === period)
    if (existing) {
      existing.slots.push(slot)
    } else {
      groups.push({ period, slots: [slot] })
    }
  }
  return groups
}

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// open slot, reveal more client-side" pattern - a clinic-wide listing with no doctor filter can
// span every Fixed-Time doctor's entire remaining inventory.
//
// patient-slot-booking-date-logic: a BookMyShow-style date-strip replaces "every upcoming date
// mixed into one long list" - the patient picks exactly one present-or-future day (defaulting
// to today) and sees only that day's slots; the backend floors every date to today-or-later
// regardless of what's selected, so there's no path to a past date even via a stale request.
export function OpenSlotList({ clinicId, doctorId }: OpenSlotListProps) {
  const [session] = useState(() => loadPatientSession())
  const [date, setDate] = useState(() => todayIsoDate())
  const [slots, setSlots] = useState<OpenSlot[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [page, setPage] = useState(0)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [selectedSlotId, setSelectedSlotId] = useState<string | null>(null)
  // patient-booking-visual-polish: holds the just-booked slot after handleBooked removes it from
  // `slots` (so it no longer resolves via selectedSlotId) - without this, BookSlotForm unmounts
  // the instant a booking succeeds and its own "Booking confirmed" state never has a chance to
  // render. Cleared whenever the patient starts a new pick (new date, new slot).
  const [bookedSlot, setBookedSlot] = useState<OpenSlot | null>(null)

  // Reset to page 0 on a genuinely new query (clinic/doctor/date changes).
  useEffect(() => {
    setPage(0)
  }, [clinicId, doctorId, date])

  useEffect(() => {
    if (!session) return
    let cancelled = false

    listOpenSlots(clinicId, session.token, doctorId, { page, size: SLOTS_PAGE_SIZE, date })
      .then((result) => {
        if (cancelled) return
        setSlots(result.slots)
        setTotalCount(result.totalCount)
      })
      .catch(() => {
        if (!cancelled) setLoadError('Could not load open slots. Please try again.')
      })

    return () => {
      cancelled = true
    }
  }, [clinicId, doctorId, date, page, session])

  function handleBooked(slot: OpenSlot, _booking: BookingResponse) {
    const remainingOnPage = slots?.filter((s) => s.slotId !== slot.slotId) ?? []
    setSlots(remainingOnPage)
    setTotalCount((prev) => Math.max(0, prev - 1))
    setSelectedSlotId(null)
    setBookedSlot(slot)
    // Booking the last remaining slot on a later page would otherwise leave that page blank.
    if (remainingOnPage.length === 0 && page > 0) setPage((current) => current - 1)
  }

  function handleSelectDate(nextDate: string) {
    setDate(nextDate)
    setSelectedSlotId(null)
    setBookedSlot(null)
  }

  function handleSelectSlot(slotId: string) {
    setSelectedSlotId(slotId)
    setBookedSlot(null)
  }

  if (!session) {
    return (
      <div className="max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please log in to see and book open slots.</p>
      </div>
    )
  }

  if (loadError) {
    return (
      <div className="max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p role="alert" className="text-sm text-red-700">
          {loadError}
        </p>
      </div>
    )
  }

  const selectedSlot = bookedSlot ?? slots?.find((s) => s.slotId === selectedSlotId) ?? null

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Open slots</h1>
        <p className="mt-0.5 text-sm text-gray-600">Pick a date, then pick a slot to book an appointment.</p>
      </div>

      <DateStrip selectedDate={date} onSelect={handleSelectDate} />

      {slots === null ? (
        <ListSkeleton rows={2} />
      ) : (
        <>
          <h2 className="text-sm font-semibold text-gray-900">{formatDateHeading(date)}</h2>

          {slots.length === 0 ? (
            <p className="max-w-md rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-600 shadow-sm">
              No open slots on this date. Try another day.
            </p>
          ) : (
            <>
              <div className="space-y-3">
                {groupByDoctor(slots).map((group) => {
                  const periods = groupByPeriod(group.slots)
                  return (
                    <div key={group.doctorProfileId} className="rounded-xl border border-gray-200 bg-white p-4 shadow-sm">
                      <div className="mb-4 flex items-center gap-3">
                        <IconBadge>
                          <DoctorIcon />
                        </IconBadge>
                        <p className="text-sm font-semibold text-gray-900">{group.doctorName}</p>
                      </div>
                      <div className="space-y-4">
                        {periods.map((periodGroup) => (
                          <div key={periodGroup.period}>
                            {periods.length > 1 && (
                              <p className="mb-2 text-xs font-medium text-gray-500">{periodGroup.period}</p>
                            )}
                            <div className="flex flex-wrap gap-2">
                              {periodGroup.slots.map((slot) => {
                                const isSelected = slot.slotId === selectedSlotId
                                return (
                                  <button
                                    key={slot.slotId}
                                    type="button"
                                    onClick={() => handleSelectSlot(slot.slotId)}
                                    aria-label={`Book ${formatTime(slot.startTime)}–${formatTime(slot.endTime)} with ${slot.doctorName}`}
                                    aria-pressed={isSelected}
                                    className={`flex min-w-[4.75rem] flex-col items-center gap-0.5 rounded-lg border px-3 py-1.5 transition-all duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2 active:scale-95 ${
                                      isSelected
                                        ? 'border-indigo-600 bg-indigo-600 shadow-md'
                                        : 'border-gray-200 bg-gray-50 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
                                    }`}
                                  >
                                    <span
                                      className={`text-sm font-semibold tabular-nums ${isSelected ? 'text-white' : 'text-gray-900'}`}
                                    >
                                      {formatTime(slot.startTime)}
                                    </span>
                                    <span className={`text-[11px] ${isSelected ? 'text-indigo-100' : 'text-gray-500'}`}>
                                      {formatDuration(slot.startTime, slot.endTime)}
                                    </span>
                                  </button>
                                )
                              })}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )
                })}
              </div>
              <PaginationControls page={page} pageSize={SLOTS_PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} itemLabel="open slots" />
            </>
          )}
        </>
      )}

      {selectedSlot && (
        <BookSlotForm
          key={selectedSlot.slotId}
          clinicId={clinicId}
          slot={selectedSlot}
          token={session.token}
          onBooked={(booking) => handleBooked(selectedSlot, booking)}
        />
      )}
    </div>
  )
}
