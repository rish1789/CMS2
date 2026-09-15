import { useEffect, useState } from 'react'
import { getQueuePositionAsPatient, getQueuePositionAsStaff, type QueuePositionResponse } from './api'
import { loadStaffSession } from '../staff-login/token'
import { loadPatientSession } from '../patient-account/token'

export type QueuePositionIndicatorProps =
  | { mode: 'staff'; clinicId: string; bookingId: string; refreshKey?: number }
  | { mode: 'patient'; bookingId: string; refreshKey?: number }

// staff-console-audit-2026-09-10 P2: this used to render nothing at all - null - whether it was
// still loading, the fetch had failed, or the booking genuinely has no queue position. A staff
// member landing here from a "Queue position" link had no way to tell "broken page" from
// "not applicable."
type Status = 'loading' | 'error' | 'not-applicable' | 'ready'

export function QueuePositionIndicator(props: QueuePositionIndicatorProps) {
  const [position, setPosition] = useState<QueuePositionResponse | null>(null)
  const [status, setStatus] = useState<Status>('loading')
  const clinicId = props.mode === 'staff' ? props.clinicId : undefined
  const { mode, bookingId, refreshKey } = props

  useEffect(() => {
    let cancelled = false

    function fetchPosition() {
      const request =
        mode === 'staff'
          ? (() => {
              const session = loadStaffSession()
              return session && clinicId ? getQueuePositionAsStaff(clinicId, bookingId, session.token) : null
            })()
          : (() => {
              const session = loadPatientSession()
              return session ? getQueuePositionAsPatient(bookingId, session.token) : null
            })()

      if (!request) return

      request
        .then((response) => {
          if (cancelled) return
          setPosition(response)
          setStatus(response.applicable ? 'ready' : 'not-applicable')
        })
        .catch(() => {
          if (!cancelled) setStatus('error')
        })
    }

    fetchPosition()
    // _diagnostics [MEDIUM] - [QUEUE_POSITION] - [NO_POLLING]: this value is computed fresh on
    // every backend call, never stored, and shifts continuously as walk-ins/bookings/completions
    // happen elsewhere in the session - a one-shot fetch goes stale immediately.
    const intervalId = setInterval(fetchPosition, 20000)

    return () => {
      cancelled = true
      clearInterval(intervalId)
    }
  }, [mode, bookingId, clinicId, refreshKey])

  if (status === 'loading') {
    return (
      <output className="block text-sm text-gray-500">
        <span className="sr-only">Loading…</span>
        <span aria-hidden="true">Checking queue position…</span>
      </output>
    )
  }

  if (status === 'error') {
    return (
      <p role="alert" className="text-sm text-red-700">
        Couldn't load the queue position. It will retry automatically.
      </p>
    )
  }

  if (status === 'not-applicable') {
    return <p className="text-sm text-gray-500">This booking isn't in a queue.</p>
  }

  return <p className="text-sm text-gray-700">Queue position: {position?.position}</p>
}
