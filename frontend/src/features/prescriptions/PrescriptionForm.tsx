import { useEffect, useState, type FormEvent } from 'react'
import {
  createPrescription,
  listPrescriptions,
  PrescriptionApiError,
  type PrescriptionItemInput,
  type PrescriptionResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'

export interface PrescriptionFormProps {
  clinicId: string
  bookingId: string
}

const EMPTY_ITEM: PrescriptionItemInput = { medicationName: '', dosage: '', frequency: '', duration: '', instructions: '' }

export function PrescriptionForm({ clinicId, bookingId }: PrescriptionFormProps) {
  const [session] = useState(() => loadStaffSession())
  const [items, setItems] = useState<PrescriptionItemInput[]>([{ ...EMPTY_ITEM }])
  const [prescriptions, setPrescriptions] = useState<PrescriptionResponse[]>([])
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
    listPrescriptions(clinicId, bookingId, session.token)
      .then((result) => {
        if (!cancelled) setPrescriptions(result)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof PrescriptionApiError && err.body.error === 'BOOKING_NOT_FOUND') {
          setBookingNotFound(true)
          return
        }
        setError(err instanceof Error ? err.message : 'Failed to load prescriptions.')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [session, clinicId, bookingId])

  function updateItem(index: number, field: keyof PrescriptionItemInput, value: string) {
    setItems((prev) => prev.map((item, i) => (i === index ? { ...item, [field]: value } : item)))
  }

  function addItem() {
    setItems((prev) => [...prev, { ...EMPTY_ITEM }])
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setError(null)

    try {
      const created = await createPrescription(clinicId, bookingId, items, session.token)
      setPrescriptions((prev) => [...prev, created])
      setItems([{ ...EMPTY_ITEM }])
    } catch (err) {
      if (err instanceof PrescriptionApiError) {
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
        <p className="text-sm text-gray-600">Please sign in as staff to write a prescription.</p>
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
      {prescriptions.length > 0 && (
        <div className="space-y-3">
          {prescriptions.map((prescription) => (
            <div key={prescription.id} className="rounded-lg border border-gray-200 bg-white p-4 shadow-sm">
              <h3 className="text-sm font-medium text-gray-900">Prescription</h3>
              <ul className="mt-2 space-y-1 text-sm text-gray-700">
                {prescription.items.map((item) => (
                  <li key={item.id}>
                    {item.medicationName} — {item.dosage}, {item.frequency}, {item.duration}
                    {item.instructions ? ` (${item.instructions})` : ''}
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      <form
        onSubmit={handleSubmit}
        className="space-y-4 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
        aria-label="Write prescription"
      >
        {error && (
          <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
            {error}
          </p>
        )}
        {/* staff-console-audit-2026-09-10 P1: these were placeholder-only fields (aria-label
            for screen readers, but the visible label vanished the instant you typed a
            character) - on a form where confusing Dosage with Frequency is a clinical
            incident, a sighted user needs the label to stay put. */}
        {items.map((item, index) => (
          <fieldset key={index} className="space-y-2 rounded-md border border-gray-200 p-3">
            <legend className="text-sm font-medium text-gray-700">Item {index + 1}</legend>
            <div>
              <label htmlFor={`medicationName-${index}`} className="block text-sm font-medium text-gray-700">
                Medication name
              </label>
              <input
                id={`medicationName-${index}`}
                required
                value={item.medicationName}
                onChange={(e) => updateItem(index, 'medicationName', e.target.value)}
                className="input mt-1"
              />
            </div>
            <div>
              <label htmlFor={`dosage-${index}`} className="block text-sm font-medium text-gray-700">
                Dosage
              </label>
              <input
                id={`dosage-${index}`}
                required
                value={item.dosage}
                onChange={(e) => updateItem(index, 'dosage', e.target.value)}
                className="input mt-1"
              />
            </div>
            <div>
              <label htmlFor={`frequency-${index}`} className="block text-sm font-medium text-gray-700">
                Frequency
              </label>
              <input
                id={`frequency-${index}`}
                required
                value={item.frequency}
                onChange={(e) => updateItem(index, 'frequency', e.target.value)}
                className="input mt-1"
              />
            </div>
            <div>
              <label htmlFor={`duration-${index}`} className="block text-sm font-medium text-gray-700">
                Duration
              </label>
              <input
                id={`duration-${index}`}
                required
                value={item.duration}
                onChange={(e) => updateItem(index, 'duration', e.target.value)}
                className="input mt-1"
              />
            </div>
            <div>
              <label htmlFor={`instructions-${index}`} className="block text-sm font-medium text-gray-700">
                Instructions <span className="font-normal text-gray-500">(optional)</span>
              </label>
              <input
                id={`instructions-${index}`}
                value={item.instructions}
                onChange={(e) => updateItem(index, 'instructions', e.target.value)}
                className="input mt-1"
              />
            </div>
          </fieldset>
        ))}
        <button
          type="button"
          onClick={addItem}
          className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
        >
          Add another item
        </button>
        <button
          type="submit"
          disabled={submitting}
          className="block rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Saving…' : 'Save prescription'}
        </button>
      </form>
    </div>
  )
}
