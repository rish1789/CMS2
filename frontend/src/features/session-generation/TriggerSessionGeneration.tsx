import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { generateSessions, AdminApiError, type GenerateSessionsResponse } from './api'
import { loadSuperAdminSession, storeSuperAdminSession } from '../super-admin/token'
import { CheckIcon, IconBadge, SessionIcon } from '../../components/adminIcons'

// _diagnostics [MAJOR] - [full-repo-audit] - [RAW_DATE]: a raw "2026-09-13" reads like an
// unfinished data dump, unlike every other admin/staff view in this app (e.g.
// PendingDoctorsList's own formatDate).
function formatDate(iso: string): string {
  const date = new Date(`${iso}T00:00:00`)
  if (Number.isNaN(date.getTime())) return iso
  return date.toLocaleDateString(undefined, { weekday: 'long', month: 'long', day: 'numeric' })
}

export function TriggerSessionGeneration() {
  const navigate = useNavigate()
  const [error, setError] = useState<string | null>(null)
  const [triggering, setTriggering] = useState(false)
  const [result, setResult] = useState<GenerateSessionsResponse | null>(null)

  // 040-super-admin-rbac-login: route entry is already gated by RequireSuperAdminSession;
  // this only re-checks for the case where it expired or was cleared mid-visit.
  function handleUnauthorized() {
    storeSuperAdminSession(null)
    navigate('/staff/login', { replace: true })
  }

  async function handleTrigger() {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setTriggering(true)
    setError(null)
    setResult(null)

    try {
      const response = await generateSessions(session.token)
      setResult(response)
    } catch (err) {
      if (err instanceof AdminApiError) {
        if (err.status === 401) {
          handleUnauthorized()
          return
        }
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setTriggering(false)
    }
  }

  return (
    <div className="mx-auto max-w-md space-y-5 rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
      <div className="flex items-start gap-3">
        <IconBadge>
          <SessionIcon />
        </IconBadge>
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Session generation</h1>
          <p className="mt-0.5 text-sm text-gray-600">
            Manually re-run nightly session generation for today's 15-day rolling horizon.
          </p>
        </div>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {result && (
        <p className="flex items-center gap-2 rounded-md bg-green-50 p-3 text-sm text-green-700">
          <CheckIcon className="h-4 w-4 shrink-0" />
          {result.sessionsCreated} session{result.sessionsCreated === 1 ? '' : 's'} created for{' '}
          {formatDate(result.runDate)}.
        </p>
      )}

      <button
        type="button"
        onClick={handleTrigger}
        disabled={triggering}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {triggering ? 'Generating…' : 'Generate sessions now'}
      </button>
    </div>
  )
}
