import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { loadStaffSession } from '../staff-login/token'
import {
  getFlagDetail,
  listFlags,
  resolveFlag,
  type FlagDetailResponse,
  type FlagStatus,
  type ProtectionFlag,
  type SignalType,
} from './api'
import { ApiError } from '../../lib/apiClient'
import { Badge } from '../../components/Badge'
import { EmptyState } from '../../components/EmptyState'
import { LoadingState } from '../../components/LoadingState'

export interface ProtectionFlagsListProps {
  clinicId: string
}

const SIGNAL_LABEL: Record<SignalType, string> = {
  HIGH_ATTEMPT_VOLUME: 'High attempt volume',
  REPEATED_CANCELLATIONS: 'Repeated cancellations',
  REPEATED_NO_SHOWS: 'Repeated no-shows',
  OVERLAPPING_APPOINTMENTS: 'Overlapping appointments',
  REPEATED_RATE_LIMIT_VIOLATIONS: 'Repeated rate-limit violations',
}

function formatInstant(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })
}

function FlagDetailPanel({ detail }: { detail: FlagDetailResponse }) {
  const { recentActivity } = detail
  return (
    <div className="space-y-3 border-t border-gray-100 bg-gray-50 p-4 text-sm">
      {recentActivity.atGlobalLimit && (
        <p className="rounded-md bg-amber-50 p-2.5 text-amber-800">
          This patient is currently at their global appointment limit ({recentActivity.globalActiveAppointmentCount}{' '}
          active appointments across all clinics).
        </p>
      )}
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        <div>
          <p className="font-medium text-gray-700">Recent bookings at this clinic</p>
          {recentActivity.recentBookings.length === 0 ? (
            <p className="text-gray-500">None.</p>
          ) : (
            <ul className="mt-1 space-y-1">
              {recentActivity.recentBookings.map((b) => (
                <li key={b.bookingId} className="text-gray-600">
                  {b.sessionDate} · {b.doctorName} · {b.status}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <p className="font-medium text-gray-700">Recent cancellations at this clinic</p>
          {recentActivity.recentCancellations.length === 0 ? (
            <p className="text-gray-500">None.</p>
          ) : (
            <ul className="mt-1 space-y-1">
              {recentActivity.recentCancellations.map((b) => (
                <li key={b.bookingId} className="text-gray-600">
                  {b.sessionDate} · {b.doctorName}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <p className="font-medium text-gray-700">Recent no-shows at this clinic</p>
          {recentActivity.recentNoShows.length === 0 ? (
            <p className="text-gray-500">None.</p>
          ) : (
            <ul className="mt-1 space-y-1">
              {recentActivity.recentNoShows.map((b) => (
                <li key={b.bookingId} className="text-gray-600">
                  {b.sessionDate} · {b.doctorName}
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <p className="font-medium text-gray-700">Rate-limit violations at this clinic</p>
          {recentActivity.rateLimitViolations.length === 0 ? (
            <p className="text-gray-500">None.</p>
          ) : (
            <ul className="mt-1 space-y-1">
              {recentActivity.rateLimitViolations.map((v, i) => (
                <li key={i} className="text-gray-600">
                  {formatInstant(v.occurredAt)}
                </li>
              ))}
            </ul>
          )}
        </div>
      </div>
    </div>
  )
}

function FlagRow({
  flag,
  clinicId,
  token,
  onResolved,
}: {
  flag: ProtectionFlag
  clinicId: string
  token: string
  onResolved: (updated: ProtectionFlag) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const [detail, setDetail] = useState<FlagDetailResponse | null>(null)
  const [loadingDetail, setLoadingDetail] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [resolving, setResolving] = useState(false)

  function toggleExpanded() {
    const next = !expanded
    setExpanded(next)
    if (next && !detail) {
      setLoadingDetail(true)
      getFlagDetail(clinicId, flag.id, token)
        .then(setDetail)
        .catch((err: unknown) => setActionError(err instanceof Error ? err.message : 'Failed to load flag detail'))
        .finally(() => setLoadingDetail(false))
    }
  }

  function handleResolve() {
    setResolving(true)
    setActionError(null)
    resolveFlag(clinicId, flag.id, token)
      .then(onResolved)
      .catch((err: unknown) => setActionError(err instanceof Error ? err.message : 'Failed to resolve flag'))
      .finally(() => setResolving(false))
  }

  return (
    <li className="rounded-lg border border-gray-200 bg-white">
      <button
        type="button"
        onClick={toggleExpanded}
        className="flex w-full items-center justify-between gap-3 p-4 text-left transition-colors duration-150 hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <span className="font-medium text-gray-900">{flag.patientDisplayName}</span>
            <Badge color={flag.status === 'OUTSTANDING' ? 'amber' : 'green'}>{SIGNAL_LABEL[flag.signalType]}</Badge>
          </div>
          <p className="mt-1 text-sm text-gray-600">{flag.reason}</p>
          <p className="mt-1 text-xs text-gray-500">Detected {formatInstant(flag.detectedAt)}</p>
        </div>
        {flag.status === 'OUTSTANDING' ? (
          <span
            role="button"
            tabIndex={0}
            onClick={(e) => {
              e.stopPropagation()
              handleResolve()
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.stopPropagation()
                handleResolve()
              }
            }}
            className="shrink-0 rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            aria-disabled={resolving}
          >
            {resolving ? 'Resolving…' : 'Mark resolved'}
          </span>
        ) : (
          <span className="shrink-0 text-xs text-gray-500">
            Resolved {flag.resolvedAt ? formatInstant(flag.resolvedAt) : ''}
          </span>
        )}
      </button>
      {actionError && (
        <p role="alert" className="border-t border-gray-100 bg-red-50 p-3 text-sm text-red-700">
          {actionError}
        </p>
      )}
      {expanded && (loadingDetail || !detail ? <LoadingState /> : <FlagDetailPanel detail={detail} />)}
    </li>
  )
}

export function ProtectionFlagsList({ clinicId }: ProtectionFlagsListProps) {
  const session = loadStaffSession()
  const token = session?.token
  const [status, setStatus] = useState<FlagStatus>('OUTSTANDING')
  const [flags, setFlags] = useState<ProtectionFlag[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [patientQuery, setPatientQuery] = useState('')

  useEffect(() => {
    if (!token) return
    setFlags(null)
    setError(null)
    listFlags(clinicId, token, { status })
      .then((result) => setFlags(result.flags))
      .catch((err: unknown) => {
        setError(err instanceof ApiError ? err.message : 'Failed to load flags')
        setFlags([])
      })
  }, [clinicId, status, token])

  if (!session) {
    return (
      <div className="mx-auto max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Please sign in as a ClinicAdmin to review booking protection.</p>
      </div>
    )
  }

  function handleResolved(updated: ProtectionFlag) {
    setFlags((current) => (current ?? []).filter((f) => f.id !== updated.id))
  }

  const visibleFlags =
    flags === null
      ? null
      : flags.filter((f) => f.patientDisplayName.toLowerCase().includes(patientQuery.trim().toLowerCase()))

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-lg font-semibold text-gray-900">Booking protection</h1>
        <Link
          to={`/staff/clinics/${clinicId}/protection/limit-override`}
          className="text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          Clinic limit
        </Link>
      </div>

      <div className="flex gap-1 rounded-lg border border-gray-200 bg-gray-50 p-1">
        {(['OUTSTANDING', 'RESOLVED'] as const).map((s) => (
          <button
            key={s}
            type="button"
            onClick={() => setStatus(s)}
            className={`rounded-md px-3 py-1.5 text-sm font-medium transition-colors duration-150 ${
              status === s ? 'bg-white text-gray-900 shadow-xs' : 'text-gray-600 hover:text-gray-900'
            }`}
          >
            {s === 'OUTSTANDING' ? 'Outstanding' : 'Resolved'}
          </button>
        ))}
      </div>

      <input
        type="search"
        value={patientQuery}
        onChange={(e) => setPatientQuery(e.target.value)}
        placeholder="Search by patient email or name"
        aria-label="Search by patient"
        className="input"
      />

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {visibleFlags === null ? (
        <LoadingState variant="list" rows={3} />
      ) : visibleFlags.length === 0 ? (
        <EmptyState
          message={
            patientQuery.trim()
              ? 'No flags match that search.'
              : status === 'OUTSTANDING'
                ? 'No outstanding flags.'
                : 'No resolved flags yet.'
          }
        />
      ) : (
        <ul className="space-y-3">
          {visibleFlags.map((flag) => (
            <FlagRow key={flag.id} flag={flag} clinicId={clinicId} token={session.token} onResolved={handleResolved} />
          ))}
        </ul>
      )}
    </div>
  )
}
