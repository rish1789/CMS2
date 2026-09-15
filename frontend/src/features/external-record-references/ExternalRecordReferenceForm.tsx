import { useEffect, useState, type FormEvent } from 'react'
import {
  createExternalRecordReference,
  listExternalRecordReferences,
  ExternalRecordReferenceApiError,
  type ExternalRecordReferenceFields,
  type ExternalRecordReferenceResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'

export interface ExternalRecordReferenceFormProps {
  clinicId: string
  bookingId: string
}

const EMPTY_FIELDS: ExternalRecordReferenceFields = { recordType: '', sourceProvider: '', recordDate: '', summary: '' }

// _diagnostics [MAJOR] - [full-repo-audit] - [RAW_DATE]: a raw "2026-01-15" reads like an
// unfinished data dump, unlike every other clinical/admin view in this app.
function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

export function ExternalRecordReferenceForm({ clinicId, bookingId }: ExternalRecordReferenceFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [fields, setFields] = useState<ExternalRecordReferenceFields>(EMPTY_FIELDS)
  const [references, setReferences] = useState<ExternalRecordReferenceResponse[]>([])
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
    listExternalRecordReferences(clinicId, bookingId, session.token)
      .then((result) => {
        if (!cancelled) setReferences(result)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof ExternalRecordReferenceApiError && err.body.error === 'BOOKING_NOT_FOUND') {
          setBookingNotFound(true)
          return
        }
        setError(err instanceof Error ? err.message : 'Failed to load references.')
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
      const created = await createExternalRecordReference(clinicId, bookingId, fields, session.token)
      setReferences((prev) => [...prev, created])
      setFields(EMPTY_FIELDS)
    } catch (err) {
      if (err instanceof ExternalRecordReferenceApiError) {
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
        <p className="text-sm text-gray-600">Please sign in as staff to record an external record reference.</p>
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

  // staff-console-audit-2026-09-10 P2: this used to be "naked" - no card, no max-w - unlike
  // every sibling staff-tool form.
  return (
    <div className="mx-auto max-w-md space-y-6">
      {references.length > 0 && (
        <ul className="space-y-2">
          {references.map((reference) => (
            <li key={reference.id} className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
              <p className="text-sm font-medium text-gray-900">
                {reference.recordType} — {reference.sourceProvider} ({formatDate(reference.recordDate)})
              </p>
              <p className="mt-1 text-sm text-gray-700">{reference.summary}</p>
            </li>
          ))}
        </ul>
      )}

      <form
        onSubmit={handleSubmit}
        className="space-y-3 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
        aria-label="Record external record reference"
      >
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        <div>
          <label htmlFor="recordType" className="block text-sm font-medium text-gray-700">
            Record type
          </label>
          <input
            id="recordType"
            required
            value={fields.recordType}
            onChange={(e) => setFields({ ...fields, recordType: e.target.value })}
            className="input mt-1"
          />
        </div>
        <div>
          <label htmlFor="sourceProvider" className="block text-sm font-medium text-gray-700">
            Source / provider
          </label>
          <input
            id="sourceProvider"
            required
            value={fields.sourceProvider}
            onChange={(e) => setFields({ ...fields, sourceProvider: e.target.value })}
            className="input mt-1"
          />
        </div>
        <div>
          <label htmlFor="recordDate" className="block text-sm font-medium text-gray-700">
            Record date
          </label>
          <input
            id="recordDate"
            type="date"
            required
            value={fields.recordDate}
            onChange={(e) => setFields({ ...fields, recordDate: e.target.value })}
            className="input mt-1"
          />
        </div>
        <div>
          <label htmlFor="summary" className="block text-sm font-medium text-gray-700">
            Summary
          </label>
          <textarea
            id="summary"
            required
            rows={3}
            value={fields.summary}
            onChange={(e) => setFields({ ...fields, summary: e.target.value })}
            className="input mt-1"
          />
        </div>
        <button
          type="submit"
          disabled={submitting}
          className="rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Saving…' : 'Save reference'}
        </button>
      </form>
    </div>
  )
}
