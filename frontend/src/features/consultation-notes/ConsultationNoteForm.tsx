import { useEffect, useState, type FormEvent } from 'react'
import {
  createConsultationNote,
  getConsultationNote,
  ConsultationNoteApiError,
  type ConsultationNoteResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'

export interface ConsultationNoteFormProps {
  clinicId: string
  bookingId: string
}

export function ConsultationNoteForm({ clinicId, bookingId }: ConsultationNoteFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [content, setContent] = useState('')
  const [note, setNote] = useState<ConsultationNoteResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  // staff-console-audit-2026-09-10 P2: distinct from `error` below - this blocks the form
  // entirely instead of showing a banner above a form that's still live and submittable for a
  // booking that doesn't exist.
  const [bookingNotFound, setBookingNotFound] = useState(false)

  useEffect(() => {
    if (!session) {
      setLoading(false)
      return
    }
    let cancelled = false
    getConsultationNote(clinicId, bookingId, session.token)
      .then((result) => {
        if (!cancelled) setNote(result)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof ConsultationNoteApiError && err.body.error === 'CONSULTATION_NOTE_NOT_FOUND') {
          return
        }
        if (err instanceof ConsultationNoteApiError && err.body.error === 'BOOKING_NOT_FOUND') {
          setBookingNotFound(true)
          return
        }
        setError(err instanceof Error ? err.message : 'Failed to load note.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [session, clinicId, bookingId])

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      const created = await createConsultationNote(clinicId, bookingId, content, session.token)
      setNote(created)
    } catch (err) {
      if (err instanceof ConsultationNoteApiError) {
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as staff to write a consultation note.</p>
      </div>
    )
  }

  if (loading) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-500">Loading…</p>
      </div>
    )
  }

  if (bookingNotFound) {
    return (
      <p role="alert" className="mx-auto max-w-md rounded-md bg-red-50 p-3 text-sm text-red-700">
        This booking could not be found.
      </p>
    )
  }

  if (note) {
    return (
      <div className="mx-auto max-w-md space-y-2 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h2 className="text-sm font-medium text-gray-900">Consultation note</h2>
        <p className="whitespace-pre-wrap text-sm text-gray-700">{note.content}</p>
      </div>
    )
  }

  // staff-console-audit-2026-09-10 P2: this used to be "naked" - no card, no max-w - unlike
  // every sibling staff-tool form.
  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-3 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Write consultation note"
    >
      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}
      <div>
        <label htmlFor="content" className="block text-sm font-medium text-gray-700">
          Consultation note
        </label>
        <textarea
          id="content"
          required
          rows={5}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="input mt-1"
        />
      </div>
      <button
        type="submit"
        disabled={submitting}
        className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Saving…' : 'Save note'}
      </button>
    </form>
  )
}
