import { useEffect, useState } from 'react'
import {
  getSessionLiveStatusAsPatient,
  getSessionLiveStatusAsStaff,
  type LiveScheduleStatus,
  type PatientLiveStatusResponse,
  type SessionLiveStatusResponse,
} from './api'
import { loadStaffSession } from '../staff-login/token'
import { STATUS_LABELS } from './liveStatusLabels'
import { loadPatientSession } from '../patient-account/token'

export type LiveScheduleStatusIndicatorProps =
  | { mode: 'staff'; clinicId: string; sessionId: string; refreshKey?: number }
  | { mode: 'patient'; bookingId: string; refreshKey?: number }

// Mirrors QueuePositionIndicator's own status model (FR-007) - distinguishes "still loading" and
// "temporarily failed, will retry automatically" from "genuinely not applicable" (Queue-mode).
type FetchStatus = 'loading' | 'error' | 'not-applicable' | 'ready'

// 061-doctor-live-status Clarification (2026-09-23, A4): reuses QueuePositionIndicator's own
// ~20s polling cadence rather than a separate interval.
const POLL_INTERVAL_MS = 20000


function deviationText(status: LiveScheduleStatus, deviationMinutes: number | null): string | null {
  if (deviationMinutes == null) return null
  if (status === 'DELAYED') return `${deviationMinutes} min late`
  if (status === 'RUNNING_EARLY') return `${deviationMinutes} min early`
  return null
}

export function LiveScheduleStatusIndicator(props: LiveScheduleStatusIndicatorProps) {
  const [staffData, setStaffData] = useState<SessionLiveStatusResponse | null>(null)
  const [patientData, setPatientData] = useState<PatientLiveStatusResponse | null>(null)
  const [fetchStatus, setFetchStatus] = useState<FetchStatus>('loading')
  const clinicId = props.mode === 'staff' ? props.clinicId : undefined
  const sessionId = props.mode === 'staff' ? props.sessionId : undefined
  const bookingId = props.mode === 'patient' ? props.bookingId : undefined
  const { mode, refreshKey } = props

  useEffect(() => {
    let cancelled = false

    function fetchOnce() {
      if (mode === 'staff') {
        const session = loadStaffSession()
        if (!session || !clinicId || !sessionId) return
        getSessionLiveStatusAsStaff(clinicId, sessionId, session.token)
          .then((response) => {
            if (cancelled) return
            setStaffData(response)
            setFetchStatus(response.applicable ? 'ready' : 'not-applicable')
          })
          .catch(() => {
            if (!cancelled) setFetchStatus('error')
          })
      } else {
        const session = loadPatientSession()
        if (!session || !bookingId) return
        getSessionLiveStatusAsPatient(bookingId, session.token)
          .then((response) => {
            if (cancelled) return
            setPatientData(response)
            setFetchStatus(response.applicable ? 'ready' : 'not-applicable')
          })
          .catch(() => {
            if (!cancelled) setFetchStatus('error')
          })
      }
    }

    fetchOnce()
    const intervalId = setInterval(fetchOnce, POLL_INTERVAL_MS)

    return () => {
      cancelled = true
      clearInterval(intervalId)
    }
  }, [mode, clinicId, sessionId, bookingId, refreshKey])

  if (fetchStatus === 'loading') {
    return (
      <output className="block text-sm text-gray-500">
        <span className="sr-only">Loading…</span>
        <span aria-hidden="true">Checking schedule status…</span>
      </output>
    )
  }

  if (fetchStatus === 'error') {
    return (
      <p role="alert" className="text-sm text-red-700">
        Couldn't load the schedule status. It will retry automatically.
      </p>
    )
  }

  // BR-016: not-applicable covers Queue-mode and a past-operational-day session alike - there is
  // no live schedule to show either way, so the indicator disappears rather than explaining itself.
  if (fetchStatus === 'not-applicable') {
    return null
  }

  if (mode === 'staff' && staffData && staffData.status) {
    const deviation = deviationText(staffData.status, staffData.deviationMinutes)
    return (
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
        <dt className="text-gray-500">Schedule Status</dt>
        <dd className="font-medium text-gray-900">
          {STATUS_LABELS[staffData.status]}
          {deviation && <span className="ml-1 text-gray-600">({deviation})</span>}
        </dd>
        {staffData.currentPatientOrdinal != null && (
          <>
            <dt className="text-gray-500">Current Patient</dt>
            <dd className="text-gray-900">{staffData.currentPatientOrdinal}</dd>
          </>
        )}
        {staffData.expectedPatientOrdinal != null && (
          <>
            <dt className="text-gray-500">Expected Patient</dt>
            <dd className="text-gray-900">{staffData.expectedPatientOrdinal}</dd>
          </>
        )}
        {staffData.firstSlotTime && (
          <>
            <dt className="text-gray-500">First Slot</dt>
            <dd className="text-gray-900 tabular-nums">{staffData.firstSlotTime.slice(0, 5)}</dd>
          </>
        )}
        {staffData.operationalDay && (
          <>
            <dt className="text-gray-500">Operational Day</dt>
            <dd className="text-gray-900">{staffData.operationalDay}</dd>
          </>
        )}
      </dl>
    )
  }

  if (mode === 'patient' && patientData) {
    return (
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-sm">
        {patientData.doctorName && (
          <>
            <dt className="text-gray-500">Doctor</dt>
            <dd className="text-gray-900">{patientData.doctorName}</dd>
          </>
        )}
        {patientData.currentPatientOrdinal != null && (
          <>
            <dt className="text-gray-500">Currently Seeing</dt>
            <dd className="text-gray-900">Patient {patientData.currentPatientOrdinal}</dd>
          </>
        )}
        {patientData.statusText && (
          <>
            <dt className="text-gray-500">Schedule Status</dt>
            <dd className="font-medium text-gray-900">{patientData.statusText}</dd>
          </>
        )}
        {patientData.estimatedWaitMinutes != null && (
          <>
            <dt className="text-gray-500">Estimated Wait</dt>
            <dd className="text-gray-900">{patientData.estimatedWaitMinutes} min</dd>
          </>
        )}
      </dl>
    )
  }

  return null
}
