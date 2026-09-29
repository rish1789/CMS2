import { useState } from 'react'
import { deleteSession, SessionDeletionApiError } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface DeleteSessionButtonProps {
  clinicId: string
  sessionId: string
  onDeleted: () => void
}

type Phase = 'idle' | 'confirming' | 'submitting' | 'blocked'

// real-bug-fix 2026-09-17: distinct from CancelSessionButton - that cancels Bookings inside a
// Session, this removes the Session/Slot rows outright, for a Session generated with wrong values
// from a since-corrected Schedule (Schedule edits are deliberately non-retroactive). Mirrors
// CancelSessionButton's identical idle/confirming/submitting/blocked shape.
export function DeleteSessionButton({ clinicId, sessionId, onDeleted }: DeleteSessionButtonProps) {
  const [session] = useState(() => loadStaffSession())
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState<string | null>(null)

  async function handleConfirm() {
    if (!session) return
    setPhase('submitting')
    setError(null)

    try {
      await deleteSession(clinicId, sessionId, session.token)
      onDeleted()
    } catch (err) {
      if (err instanceof SessionDeletionApiError && err.body.error === 'SESSION_DELETION_BLOCKED') {
        setPhase('blocked')
        setError(err.message)
        return
      }
      setPhase('confirming')
      setError(err instanceof SessionDeletionApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to delete this session.</p>
  }

  if (phase === 'blocked' && error) {
    return (
      <p role="alert" className="text-sm text-gray-500">
        {error}
      </p>
    )
  }

  if (phase === 'confirming' || phase === 'submitting') {
    return (
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm text-gray-700">Delete this session permanently?</span>
        <button
          type="button"
          onClick={handleConfirm}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg bg-red-600 px-3 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {phase === 'submitting' ? 'Deleting…' : 'Confirm'}
        </button>
        <button
          type="button"
          onClick={() => {
            setPhase('idle')
            setError(null)
          }}
          disabled={phase === 'submitting'}
          className="inline-flex h-9 items-center rounded-lg border border-gray-300 px-3 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:cursor-not-allowed disabled:opacity-50"
        >
          Back
        </button>
        {error && (
          <p role="alert" className="w-full text-sm text-red-700">
            {error}
          </p>
        )}
      </div>
    )
  }

  return (
    <button
      type="button"
      onClick={() => setPhase('confirming')}
      className="inline-flex h-9 items-center gap-1.5 rounded-lg border border-red-300 bg-white px-3 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100"
    >
      <svg aria-hidden="true" width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path
          d="M2.5 3.5h9M5.25 3.5V2.4c0-.5.4-.9.9-.9h1.7c.5 0 .9.4.9.9v1.1M5.8 6.3v4M8.2 6.3v4M3.3 3.5l.5 8c.03.5.45.9.95.9h4.4c.5 0 .92-.4.95-.9l.5-8"
          stroke="currentColor"
          strokeWidth="1.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      Delete session
    </button>
  )
}
