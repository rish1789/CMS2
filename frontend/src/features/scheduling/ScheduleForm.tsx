import { useState, type FormEvent } from 'react'
import { createSchedule, editSchedule, type ScheduleApiErrorBody, type ScheduleMode, type ScheduleResponse } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'
import { ApiError } from '../../lib/apiClient'
import { FormField } from '../../components/FormField'

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
  hasBreak: boolean
  breakStartTime: string
  breakEndTime: string
}

interface FieldErrors {
  daysOfWeek?: string
  endTime?: string
  breakEndTime?: string
}

const initialState: FormState = {
  daysOfWeek: [],
  startTime: '',
  endTime: '',
  mode: 'FIXED_TIME',
  slotIntervalMinutes: '',
  hasBreak: false,
  breakStartTime: '',
  breakEndTime: '',
}

// _diagnostics [MEDIUM] - [SCHEDULE_EDIT] - [ORPHANED_COMPONENT]: ScheduleController's tested
// PATCH edit endpoint had no component that could invoke it at all - editSchedule() existed in
// api.ts with zero callers. Passing existingSchedule switches this form into edit mode in place,
// rather than adding a whole separate EditScheduleForm.tsx duplicating this one.
export interface ScheduleFormProps {
  clinicId: string
  doctorProfileId: string
  existingSchedule?: ScheduleResponse
  // real-bug-fix 2026-09-17: lets DoctorScheduleManager embed this form inline (per-row edit,
  // or a toggled "add another schedule" panel) instead of the standalone dead-end result screen
  // below - only used when the caller actually wants that, so the original standalone route
  // behavior (and its existing tests) is untouched when these are omitted.
  onSaved?: (schedule: ScheduleResponse) => void
  onCancel?: () => void
}

export function ScheduleForm({ clinicId, doctorProfileId, existingSchedule, onSaved, onCancel }: ScheduleFormProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(() =>
    existingSchedule
      ? {
          daysOfWeek: existingSchedule.daysOfWeek,
          // real-bug-fix 2026-09-17: existingSchedule.startTime/endTime come back "HH:MM:SS" -
          // an <input type="time"> with the default step (60s) needs "HH:MM", not "HH:MM:SS".
          // Never caught before since existingSchedule had no real caller until now.
          startTime: formatTime(existingSchedule.startTime),
          endTime: formatTime(existingSchedule.endTime),
          mode: existingSchedule.mode,
          slotIntervalMinutes: existingSchedule.slotIntervalMinutes?.toString() ?? '',
          hasBreak: Boolean(existingSchedule.breakStartTime && existingSchedule.breakEndTime),
          breakStartTime: existingSchedule.breakStartTime ? formatTime(existingSchedule.breakStartTime) : '',
          breakEndTime: existingSchedule.breakEndTime ? formatTime(existingSchedule.breakEndTime) : '',
        }
      : initialState,
  )
  const [formError, setFormError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
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
    setFormError(null)

    // Mirrors ScheduleService.java's own checks (research.md Decision 3) so the same failure
    // surfaces inline before a network round-trip, not only after the backend rejects it.
    const errors: FieldErrors = {}
    if (form.daysOfWeek.length === 0) {
      errors.daysOfWeek = 'At least one day of the week is required'
    }
    if (form.startTime && form.endTime && form.startTime >= form.endTime) {
      errors.endTime = 'startTime must be strictly before endTime'
    }
    if (form.hasBreak) {
      if (!form.breakStartTime || !form.breakEndTime) {
        errors.breakEndTime = 'Both break start and end times are required'
      } else if (form.breakStartTime >= form.breakEndTime) {
        errors.breakEndTime = 'breakStartTime must be strictly before breakEndTime'
      } else if (form.breakStartTime < form.startTime || form.breakEndTime > form.endTime) {
        errors.breakEndTime = 'The break window must fall within start time and end time'
      }
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    setSubmitting(true)

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
        breakStartTime: form.hasBreak && form.breakStartTime !== '' ? form.breakStartTime : undefined,
        breakEndTime: form.hasBreak && form.breakEndTime !== '' ? form.breakEndTime : undefined,
      }
      const response = existingSchedule
        ? await editSchedule(clinicId, doctorProfileId, existingSchedule.id, payload, session.token)
        : await createSchedule(clinicId, doctorProfileId, payload, session.token)
      if (onSaved) {
        onSaved(response)
      } else {
        setResult(response)
      }
    } catch (err) {
      if (err instanceof ApiError) {
        const body = err.body as ScheduleApiErrorBody | undefined
        if (body?.error === 'UNAUTHORIZED') {
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
          , {formatTime(result.startTime)}–{formatTime(result.endTime)}
          {result.breakStartTime && result.breakEndTime && (
            <> (break {formatTime(result.breakStartTime)}–{formatTime(result.breakEndTime)})</>
          )}
          , {result.daysOfWeek.map(formatDayLabel).join(', ')}.
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
        {fieldErrors.daysOfWeek && (
          <p role="alert" className="mt-2 text-sm text-red-600">
            {fieldErrors.daysOfWeek}
          </p>
        )}
      </fieldset>

      <div className="grid grid-cols-2 gap-4">
        <FormField label="Start time" htmlFor="startTime">
          <input
            id="startTime"
            type="time"
            required
            value={form.startTime}
            onChange={(e) => updateField('startTime', e.target.value)}
            className="input"
          />
        </FormField>
        <FormField label="End time" htmlFor="endTime" error={fieldErrors.endTime}>
          <input
            id="endTime"
            type="time"
            required
            value={form.endTime}
            onChange={(e) => updateField('endTime', e.target.value)}
            className="input"
          />
        </FormField>
      </div>

      <FormField label="Mode" htmlFor="mode">
        <select
          id="mode"
          value={form.mode}
          onChange={(e) => updateField('mode', e.target.value as ScheduleMode)}
          className="input"
        >
          <option value="FIXED_TIME">Fixed-Time</option>
          <option value="QUEUE">Queue/Token</option>
        </select>
      </FormField>

      {form.mode === 'FIXED_TIME' && (
        <>
          <FormField label="Slot interval (minutes)" htmlFor="slotIntervalMinutes">
            <input
              id="slotIntervalMinutes"
              type="number"
              min={1}
              required
              value={form.slotIntervalMinutes}
              onChange={(e) => updateField('slotIntervalMinutes', e.target.value)}
              className="input"
            />
          </FormField>

          <div>
            <label className="flex items-center gap-2 text-sm font-medium text-gray-700">
              <input
                type="checkbox"
                checked={form.hasBreak}
                onChange={(e) => updateField('hasBreak', e.target.checked)}
              />
              Add a break (e.g. lunch)
            </label>
            {form.hasBreak && (
              <div className="mt-2 grid grid-cols-2 gap-4">
                <FormField label="Break start" htmlFor="breakStartTime">
                  <input
                    id="breakStartTime"
                    type="time"
                    required
                    value={form.breakStartTime}
                    onChange={(e) => updateField('breakStartTime', e.target.value)}
                    className="input"
                  />
                </FormField>
                <FormField label="Break end" htmlFor="breakEndTime" error={fieldErrors.breakEndTime}>
                  <input
                    id="breakEndTime"
                    type="time"
                    required
                    value={form.breakEndTime}
                    onChange={(e) => updateField('breakEndTime', e.target.value)}
                    className="input"
                  />
                </FormField>
              </div>
            )}
          </div>
        </>
      )}

      <div className="flex gap-2">
        <button
          type="submit"
          disabled={submitting}
          className="flex-1 rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow-md active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Saving…' : isEditing ? 'Save changes' : 'Save schedule'}
        </button>
        {onCancel && (
          <button
            type="button"
            onClick={onCancel}
            disabled={submitting}
            className="rounded-lg border border-gray-300 bg-white px-4 py-2.5 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:opacity-50"
          >
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
