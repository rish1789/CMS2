import { useEffect, useState } from 'react'
import { listSchedules, type ScheduleResponse } from './api'
import { ScheduleForm } from './ScheduleForm'
import { DeleteScheduleButton } from './DeleteScheduleButton'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'

const DAY_ABBREVIATIONS: Record<string, string> = {
  MONDAY: 'Mon',
  TUESDAY: 'Tue',
  WEDNESDAY: 'Wed',
  THURSDAY: 'Thu',
  FRIDAY: 'Fri',
  SATURDAY: 'Sat',
  SUNDAY: 'Sun',
}

const DAY_ORDER = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']

function formatTime(time: string): string {
  return time.slice(0, 5)
}

function summarize(schedule: ScheduleResponse): string {
  const days = [...schedule.daysOfWeek]
    .sort((a, b) => DAY_ORDER.indexOf(a) - DAY_ORDER.indexOf(b))
    .map((d) => DAY_ABBREVIATIONS[d] ?? d)
    .join(', ')
  const modeLabel =
    schedule.mode === 'FIXED_TIME' ? `Fixed-Time, ${schedule.slotIntervalMinutes}-min slots` : 'Queue/Token'
  const breakLabel =
    schedule.breakStartTime && schedule.breakEndTime
      ? ` (break ${formatTime(schedule.breakStartTime)}–${formatTime(schedule.breakEndTime)})`
      : ''
  return `${days} · ${formatTime(schedule.startTime)}–${formatTime(schedule.endTime)}${breakLabel} · ${modeLabel}`
}

export interface DoctorScheduleManagerProps {
  clinicId: string
  doctorProfileId: string
}

// real-bug-fix 2026-09-17: ScheduleForm's existingSchedule/edit-mode prop was fully built and
// tested (016-schedule-edit-non-retroactivity) but no page ever fetched a doctor's schedules and
// passed one in - DefineSchedulePage always rendered a blank create form, even when a schedule
// already existed, which is exactly what made a genuinely wrong schedule (Furaka Singh's PM
// shift entered as 16:00-20:00 instead of the real 16:00-18:00) impossible to see or correct.
// A doctor with a long midday break (e.g. 9:30-14:00, then 16:00-18:00) needs two Schedule rows
// - one per contiguous working block - since a single row can't express a gap; this page lists
// every existing row for the doctor and lets staff edit any one of them in place, or add another.
export function DoctorScheduleManager({ clinicId, doctorProfileId }: DoctorScheduleManagerProps) {
  const [session] = useState(() => loadStaffSession())
  const [schedules, setSchedules] = useState<ScheduleResponse[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [addingNew, setAddingNew] = useState(false)

  function refresh() {
    if (!session) return
    listSchedules(clinicId, doctorProfileId, session.token)
      .then(setSchedules)
      .catch((err: unknown) => setError(err instanceof Error ? err.message : 'Failed to load schedules.'))
  }

  useEffect(refresh, [session, clinicId, doctorProfileId])

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to manage schedules.</p>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-md space-y-6">
      <div className="rounded-xl border border-gray-200 bg-white p-6 shadow-md">
        <h1 className="text-lg font-semibold text-gray-900">Schedules</h1>

        {error && (
          <p role="alert" className="mt-3 rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        {notice && <p className="mt-3 rounded-md bg-green-50 p-3 text-sm text-green-700">{notice}</p>}

        {schedules === null ? (
          <div className="mt-3">
            <ListSkeleton rows={2} />
          </div>
        ) : schedules.length === 0 && !addingNew ? (
          <p className="mt-3 text-sm text-gray-600">No schedules yet.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {schedules.map((schedule) =>
              editingId === schedule.id ? (
                <li key={schedule.id} className="rounded-lg border border-indigo-200 p-3">
                  <ScheduleForm
                    clinicId={clinicId}
                    doctorProfileId={doctorProfileId}
                    existingSchedule={schedule}
                    onSaved={() => {
                      setEditingId(null)
                      setNotice('Schedule updated.')
                      refresh()
                    }}
                    onCancel={() => setEditingId(null)}
                  />
                </li>
              ) : (
                <li
                  key={schedule.id}
                  className="flex items-center justify-between gap-3 rounded-lg border border-gray-200 bg-gray-50 px-3 py-2"
                >
                  <span className="text-sm text-gray-900">{summarize(schedule)}</span>
                  <div className="flex shrink-0 items-center gap-3">
                    <button
                      type="button"
                      onClick={() => setEditingId(schedule.id)}
                      className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
                    >
                      Edit
                    </button>
                    <DeleteScheduleButton
                      clinicId={clinicId}
                      doctorProfileId={doctorProfileId}
                      scheduleId={schedule.id}
                      onDeleted={() => {
                        setNotice('Schedule deleted.')
                        refresh()
                      }}
                    />
                  </div>
                </li>
              ),
            )}
          </ul>
        )}
      </div>

      {addingNew ? (
        <ScheduleForm
          clinicId={clinicId}
          doctorProfileId={doctorProfileId}
          onSaved={() => {
            setAddingNew(false)
            setNotice('Schedule created.')
            refresh()
          }}
          onCancel={() => setAddingNew(false)}
        />
      ) : (
        <button
          type="button"
          onClick={() => setAddingNew(true)}
          className="w-full rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50"
        >
          Add another schedule
        </button>
      )}
    </div>
  )
}
