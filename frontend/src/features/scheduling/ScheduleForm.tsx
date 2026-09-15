import { useState, type FormEvent } from 'react'
import { createSchedule, editSchedule, ScheduleApiError, type ScheduleMode, type ScheduleResponse } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'

const DAYS = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'] as const

// staff-console-audit-2026-09-10 P3: display only - the value sent to the API and stored in
// form state stays the raw enum ("MONDAY"), matching what the backend expects.
function formatDayLabel(day: string): string {
  return day.charAt(0) + day.slice(1).toLowerCase()
}

// "09:00:00" -> "09:00" - the seconds ScheduleResponse returns are never meaningful here.
function formatTime(time: string): string {
  return time.slice(0, 5)
}

interface FormState {
  daysOfWeek: string[]
  startTime: string
  endTime: string
  mode: ScheduleMode
  slotIntervalMinutes: string
}

const initialState: FormState = {
  daysOfWeek: [],
  startTime: '',
  endTime: '',
  mode: 'FIXED_TIME',
  slotIntervalMinutes: '',
}

// _diagnostics [MEDIUM] - [SCHEDULE_EDIT] - [ORPHANED_COMPONENT]: ScheduleController's tested
// PATCH edit endpoint had no component that could invoke it at all - editSchedule() existed in
// api.ts with zero callers. Passing existingSchedule switches this form into edit mode in place,
// rather than adding a whole separate EditScheduleForm.tsx duplicating this one.
export interface ScheduleFormProps {
  clinicId: string
  doctorProfileId: string
  existingSchedule?: ScheduleResponse
}

export function ScheduleForm({ clinicId, doctorProfileId, existingSchedule }: ScheduleFormProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(() =>
    existingSchedule
      ? {
          daysOfWeek: existingSchedule.daysOfWeek,
          startTime: existingSchedule.startTime,
          endTime: existingSchedule.endTime,
          mode: existingSchedule.mode,
          slotIntervalMinutes: existingSchedule.slotIntervalMinutes?.toString() ?? '',
        }
      : initialState,
  )
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<ScheduleResponse | null>(null)
  const isEditing = existingSchedule !== undefined

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function toggleDay(day: string) {
    setForm((prev) => ({
      ...prev,
      daysOfWeek: prev.daysOfWeek.includes(day)
        ? prev.daysOfWeek.filter((d) => d !== day)
        : [...prev.daysOfWeek, day],
    }))
  }

  function handleSessionExpired(message: string) {
    storeStaffSession(null)
    setSession(null)
    setFormError(message)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setFormError(null)

    try {
      const payload = {
        daysOfWeek: form.daysOfWeek,
        startTime: form.startTime,
        endTime: form.endTime,
        mode: form.mode,
        slotIntervalMinutes:
          form.mode === 'FIXED_TIME' && form.slotIntervalMinutes.trim() !== ''
            ? Number(form.slotIntervalMinutes)
            : undefined,
      }
      const response = existingSchedule
        ? await editSchedule(clinicId, doctorProfileId, existingSchedule.id, payload, session.token)
        : await createSchedule(clinicId, doctorProfileId, payload, session.token)
      setResult(response)
    } catch (err) {
      if (err instanceof ScheduleApiError) {
        if (err.body.error === 'UNAUTHORIZED') {
          handleSessionExpired(err.message)
        } else {
          setFormError(err.message)
        }
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to define a schedule.</p>
      </div>
    )
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-xl border border-gray-200 bg-white p-6 shadow-md">
        <h1 className="text-lg font-semibold text-gray-900">{isEditing ? 'Schedule updated' : 'Schedule created'}</h1>
        <p className="mt-2 text-sm text-gray-600 tabular-nums">
          {result.mode === 'FIXED_TIME'
            ? `Fixed-Time, ${result.slotIntervalMinutes}-minute slots`
            : 'Queue/Token'}
          , {formatTime(result.startTime)}–{formatTime(result.endTime)}, {result.daysOfWeek.map(formatDayLabel).join(', ')}.
        </p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-md"
      aria-label="Define recurring schedule"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">
          {isEditing ? 'Edit schedule' : 'Define a recurring schedule'}
        </h1>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <fieldset>
        <legend className="mb-2 block text-sm font-medium text-gray-700">Days of week</legend>
        <div className="flex flex-wrap gap-2">
          {DAYS.map((day) => {
            const isSelected = form.daysOfWeek.includes(day)
            return (
              <label
                key={day}
                className={`cursor-pointer rounded-lg border px-3 py-1.5 text-sm font-medium transition-all duration-200 ease-out ${
                  isSelected
                    ? 'border-indigo-600 bg-indigo-600 text-white shadow-md'
                    : 'border-gray-200 bg-gray-50 text-gray-700 hover:-translate-y-0.5 hover:border-indigo-300 hover:bg-white hover:shadow-sm'
                }`}
              >
                <input type="checkbox" checked={isSelected} onChange={() => toggleDay(day)} className="sr-only" />
                {formatDayLabel(day)}
              </label>
            )
          })}
        </div>
      </fieldset>

      <div className="grid grid-cols-2 gap-4">
        <div>
          <label htmlFor="startTime" className="block text-sm font-medium text-gray-700">
            Start time
          </label>
          <input
            id="startTime"
            type="time"
            required
            value={form.startTime}
            onChange={(e) => updateField('startTime', e.target.value)}
            className="input mt-1"
          />
        </div>
        <div>
          <label htmlFor="endTime" className="block text-sm font-medium text-gray-700">
            End time
          </label>
          <input
            id="endTime"
            type="time"
            required
            value={form.endTime}
            onChange={(e) => updateField('endTime', e.target.value)}
            className="input mt-1"
          />
        </div>
      </div>

      <div>
        <label htmlFor="mode" className="block text-sm font-medium text-gray-700">
          Mode
        </label>
        <select
          id="mode"
          value={form.mode}
          onChange={(e) => updateField('mode', e.target.value as ScheduleMode)}
          className="input mt-1"
        >
          <option value="FIXED_TIME">Fixed-Time</option>
          <option value="QUEUE">Queue/Token</option>
        </select>
      </div>

      {form.mode === 'FIXED_TIME' && (
        <div>
          <label htmlFor="slotIntervalMinutes" className="block text-sm font-medium text-gray-700">
            Slot interval (minutes)
          </label>
          <input
            id="slotIntervalMinutes"
            type="number"
            min={1}
            required
            value={form.slotIntervalMinutes}
            onChange={(e) => updateField('slotIntervalMinutes', e.target.value)}
            className="input mt-1"
          />
        </div>
      )}

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Saving…' : isEditing ? 'Save changes' : 'Save schedule'}
      </button>
    </form>
  )
}
