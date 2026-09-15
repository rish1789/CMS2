import { useEffect, useState } from 'react'
import { getSessionDelay, type SessionDelayResponse } from './api'
import { loadStaffSession } from '../staff-login/token'

export interface DelayIndicatorProps {
  clinicId: string
  sessionId: string
  /** Bump this to re-fetch after a trigger point (e.g. a completion or walk-in insertion) elsewhere on the page. */
  refreshKey?: number
}

export function DelayIndicator({ clinicId, sessionId, refreshKey }: DelayIndicatorProps) {
  const [delay, setDelay] = useState<SessionDelayResponse | null>(null)

  useEffect(() => {
    const session = loadStaffSession()
    if (!session) return
    let cancelled = false

    getSessionDelay(clinicId, sessionId, session.token)
      .then((response) => {
        if (!cancelled) setDelay(response)
      })
      .catch(() => {
        if (!cancelled) setDelay(null)
      })

    return () => {
      cancelled = true
    }
  }, [clinicId, sessionId, refreshKey])

  if (!delay || !delay.applicable) {
    return null
  }

  if (delay.delayMinutes == null) {
    return <p className="text-sm text-green-700">On time</p>
  }

  return <p className="text-sm text-amber-700">Running {delay.delayMinutes} min behind</p>
}
