import { useEffect, useState, type FormEvent } from 'react'
import {
  createConsultationNote,
  getConsultationNote,
  type ConsultationNoteErrorBody,
  type ConsultationNoteResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'
import { ApiError } from '../../lib/apiClient'
import { FormField } from '../../components/FormField'

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
  const [contentError, setContentError] = useState<string | null>(null)
  // staff-console-audit-2026-09-10 P2: distinct from `error` below - this blocks the form
  // entirely instead of showing a banner above a form that's still live and submittable for a
  // booking that doesn't exist.
  const [bookingNotFound, setBookingNotFound] = useState(false)
  // real-bug-fix 2026-09-17: same reasoning as bookingNotFound above - a FORBIDDEN response
  // (anyone but the treating doctor) used to fall through to the generic `error` banner while
  // the textarea and Save button stayed live and submittable underneath it, inviting a doomed
  // resubmit of the same denied request.
  const [accessDeniedMessage, setAccessDeniedMessage] = useState<string | null>(null)

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
        const body = err instanceof ApiError ? (err.body as ConsultationNoteErrorBody | undefined) : undefined
        if (body?.error === 'CONSULTATION_NOTE_NOT_FOUND') {
          return
        }
        if (body?.error === 'BOOKING_NOT_FOUND') {
          setBookingNotFound(true)
          return
        }
        if (body?.error === 'FORBIDDEN') {
          setAccessDeniedMessage(err instanceof Error ? err.message : 'Only the treating doctor may write or view this note.')
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
    setError(null)

    if (content.trim() === '') {
      setContentError('Consultation note content cannot be blank.')
      return
    }
    setContentError(null)

    setSubmitting(true)

    try {
      const created = await createConsultationNote(clinicId, bookingId, content, session.token)
      setNote(created)
    } catch (err) {
      if (err instanceof ApiError) {
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

  if (accessDeniedMessage) {
    return (
      <p role="alert" className="mx-auto max-w-md rounded-md bg-red-50 p-3 text-sm text-red-700">
        {accessDeniedMessage}
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
      <FormField label="Consultation note" htmlFor="content" error={contentError ?? undefined}>
        <textarea
          id="content"
          required
          rows={5}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          className="input"
        />
      </FormField>
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
