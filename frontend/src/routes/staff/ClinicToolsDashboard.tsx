import { useEffect, useState } from 'react'
import { Link, useOutletContext, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { loadStaffSession } from '../../features/staff-login/token'
import type { StaffRole } from '../../components/RoleBadge'
import type { ClinicShellOutletContext } from './ClinicShell'
import { CLINIC_TOOL_ROLES, hasAnyRole } from './clinicRoles'
import { getTodayStats, listSessions, type SessionSummary, type TodaySessionStats } from '../../features/day-sheet/api'
import { listInboxItems } from '../../features/inbox/api'
import { getWaitlistCount } from '../../features/waitlist/api'
import { IconBadge } from '../../components/adminIcons'
import { Badge } from '../../components/Badge'
import { Card } from '../../components/Card'
import { EmptyState } from '../../components/EmptyState'
import { LoadingState } from '../../components/LoadingState'
import {
  ClockIcon,
  DaySheetIcon,
  InboxIcon,
  SearchIcon,
  ShieldIcon,
  StethoscopeIcon,
  TeamIcon,
  UserPlusIcon,
} from '../../components/staffIcons'

// 051-staff-dashboard-enhancement US1: a condensed glance, not the full Day Sheet - capped
// rather than paginated (research.md).
const MAX_SESSIONS_SHOWN = 10

// Local, deliberately not exported from DaySheet.tsx - a 3-line pure formatter isn't worth
// coupling two unrelated route components over (tasks.md T002).
function formatSessionTimeRange(startTime: string | null | undefined, endTime: string | null | undefined): string {
  if (!startTime || !endTime) return ''
  return `${startTime.slice(0, 5)}–${endTime.slice(0, 5)}`
}

// 051-staff-dashboard-enhancement T011 (contracts/today-session-stats.md): both counts real,
// server-computed - never fabricated or client-estimated (FR-004).
function TodayStatsTile({ stats }: { stats: TodaySessionStats | null }) {
  return (
    <div className="flex gap-3">
      <div className="flex-1 rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-xs font-semibold tracking-wide text-gray-500 uppercase">Completed today</p>
        {stats === null ? (
          <span aria-hidden="true" className="mt-1 block h-7 w-10 animate-pulse rounded bg-gray-100" />
        ) : (
          <p className="mt-1 text-2xl font-semibold text-gray-900">{stats.completedCount}</p>
        )}
      </div>
      <div className="flex-1 rounded-lg border border-gray-200 bg-white p-4">
        <p className="text-xs font-semibold tracking-wide text-gray-500 uppercase">No-shows today</p>
        {stats === null ? (
          <span aria-hidden="true" className="mt-1 block h-7 w-10 animate-pulse rounded bg-gray-100" />
        ) : (
          <p className="mt-1 text-2xl font-semibold text-gray-900">{stats.noShowCount}</p>
        )}
      </div>
    </div>
  )
}

function TodaySessionsSection({
  clinicId,
  sessions,
  totalCount,
}: {
  clinicId: string
  sessions: SessionSummary[] | null
  totalCount: number | null
}) {
  if (sessions === null) {
    return (
      <div className="space-y-2">
        <h2 className="text-xs font-semibold tracking-wide text-gray-500 uppercase">Today's sessions</h2>
        <LoadingState variant="list" rows={2} />
      </div>
    )
  }

  return (
    <div className="space-y-2">
      <h2 className="text-xs font-semibold tracking-wide text-gray-500 uppercase">Today's sessions</h2>
      {sessions.length === 0 ? (
        <EmptyState message="No sessions scheduled today." />
      ) : (
        <div className="divide-y divide-gray-100 rounded-lg border border-gray-200 bg-white">
          {sessions.map((sessionItem) => (
            <Link
              key={sessionItem.sessionId}
              to={`/staff/clinics/${clinicId}/day-sheet/${sessionItem.sessionId}`}
              className="flex items-center justify-between gap-3 p-3 text-sm transition-colors duration-150 hover:bg-gray-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              <div className="flex min-w-0 items-center gap-3">
                <span className="font-medium text-gray-900">{sessionItem.doctorName}</span>
                <span className="text-gray-500">
                  {formatSessionTimeRange(sessionItem.startTime, sessionItem.endTime)}
                </span>
                <Badge color={sessionItem.mode === 'FIXED_TIME' ? 'indigo' : 'gray'}>
                  {sessionItem.mode === 'FIXED_TIME' ? 'Fixed-time' : 'Queue'}
                </Badge>
              </div>
              <span className="shrink-0 text-gray-500">
                {sessionItem.bookedSlotCount}/{sessionItem.totalSlotCount} booked
              </span>
            </Link>
          ))}
        </div>
      )}
      {totalCount !== null && totalCount > sessions.length && (
        <Link
          to={`/staff/clinics/${clinicId}/day-sheet`}
          className="block text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          +{totalCount - sessions.length} more in Day Sheet
        </Link>
      )}
    </div>
  )
}

// 041-staff-console-pickers: replaces most of the prior "type a raw ID, click Go" launcher
// grid with links into real browse/pick views. Only two tools that never had a typed-ID field
// remain as plain links here ("Onboard staff", "Join a patient to the waitlist") - everything
// else now starts from the Day Sheet, Doctor picker, Staff picker, or Patient search.

interface QuickLink {
  title: string
  description: string
  path: (clinicId: string) => string
  icon: ReactNode
  /** 073-role-aware-clinic-tools: omit for every role; otherwise shown only once roles are known and allowed. */
  roles?: readonly StaffRole[]
}

const LINKS: QuickLink[] = [
  {
    title: 'Day sheet',
    description: "Browse this clinic's sessions — book, cancel, walk-ins, queue, and clinical documentation all start here.",
    path: (clinicId) => `/staff/clinics/${clinicId}/day-sheet`,
    icon: <DaySheetIcon />,
  },
  {
    title: 'Doctors',
    description: 'Define a schedule or manage appointment types for a doctor staffed at this clinic.',
    path: (clinicId) => `/staff/clinics/${clinicId}/doctors`,
    icon: <StethoscopeIcon />,
  },
  {
    title: 'Staff',
    // staff-console-audit-2026-09-10 P2: previously described the page purely by its
    // destructive action, with no hint that this is also where you look someone up.
    description: 'View the clinic roster, or deactivate a staff member.',
    path: (clinicId) => `/staff/clinics/${clinicId}/staff`,
    icon: <TeamIcon />,
  },
  {
    title: 'Find a patient',
    description: 'Search by name or phone to look up a patient, or anonymize a record on request.',
    path: (clinicId) => `/staff/clinics/${clinicId}/patients/search`,
    icon: <SearchIcon />,
  },
  {
    title: 'Onboard staff',
    // staff-console-audit-2026-09-10 P2: this promised a role the onboarding form (and backend)
    // has never actually supported - ClinicAdmin accounts are created via clinic registration.
    description: 'Add a new Doctor or Operations staff member.',
    path: (clinicId) => `/staff/clinics/${clinicId}/onboard`,
    icon: <UserPlusIcon />,
    roles: CLINIC_TOOL_ROLES.onboard,
  },
  {
    title: 'Join a patient to the waitlist',
    description: 'Add a patient to the waitlist for a doctor/specialization.',
    path: (clinicId) => `/staff/clinics/${clinicId}/waitlist/join`,
    icon: <ClockIcon />,
  },
  {
    title: 'Booking protection',
    description: 'Review flagged suspicious booking activity and manage this clinic’s appointment limit.',
    path: (clinicId) => `/staff/clinics/${clinicId}/protection`,
    icon: <ShieldIcon />,
    roles: CLINIC_TOOL_ROLES.protection,
  },
]

function todayIsoDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

// 051-staff-dashboard-enhancement T013 (046 restyle): visual only - same null-while-loading /
// resolved-count behavior as before, now rendered via the shared Badge component.
function CountBadge({ count, singular, plural }: { count: number | null; singular: string; plural: string }) {
  if (count === null) {
    return <span aria-hidden="true" className="h-4 w-16 animate-pulse rounded-full bg-gray-100" />
  }
  return (
    <Badge color="gray">
      {count} {count === 1 ? singular : plural}
    </Badge>
  )
}

// dashboard-live-data-2026-09-10: the audit's own words - "the dashboard is a table of contents,
// not an operational home... zero live data (no today's session count, no queue length, no
// inbox badge)". Each count is fetched independently so one failing (e.g. a clinic with no
// waitlist activity ever) never blocks the other two - a stuck loading pulse on just that one
// badge, not a page-wide error.
export function ClinicToolsDashboard() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [todaySessionCount, setTodaySessionCount] = useState<number | null>(null)
  const [todaySessions, setTodaySessions] = useState<SessionSummary[] | null>(null)
  const [unclaimedInboxCount, setUnclaimedInboxCount] = useState<number | null>(null)
  const [waitingCount, setWaitingCount] = useState<number | null>(null)
  const [todayStats, setTodayStats] = useState<TodaySessionStats | null>(null)
  // Undefined when rendered outside ClinicShell - then no restricted tile is offered.
  const shell = useOutletContext<ClinicShellOutletContext | undefined>()
  const visibleLinks = LINKS.filter(
    (link) => !link.roles || (shell?.rolesStatus === 'ready' && hasAnyRole(shell.roles, link.roles)),
  )

  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    const today = todayIsoDate()

    listSessions(clinicId, session.token, { from: today, to: today, page: 0, size: MAX_SESSIONS_SHOWN })
      .then((result) => {
        setTodaySessionCount(result.totalCount)
        setTodaySessions(result.sessions)
      })
      .catch(() => {})

    listInboxItems(clinicId, session.token)
      .then((items) => setUnclaimedInboxCount(items.filter((item) => item.status === 'UNCLAIMED').length))
      .catch(() => {})

    getWaitlistCount(clinicId, session.token)
      .then((result) => setWaitingCount(result.waitingCount))
      .catch(() => {})

    getTodayStats(clinicId, session.token)
      .then((result) => setTodayStats(result))
      .catch(() => {})
  }, [clinicId])

  if (!clinicId) return null

  return (
    <div className="space-y-8">
      <Link
        to={`/staff/clinics/${clinicId}/inbox`}
        className="flex items-center gap-4 rounded-lg border border-indigo-200 bg-indigo-50 p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-white text-indigo-600 shadow-xs">
          <InboxIcon />
        </span>
        <div className="min-w-0 flex-1">
          <h2 className="flex items-center gap-2 text-sm font-semibold text-indigo-900">
            Inbox
            {unclaimedInboxCount !== null && unclaimedInboxCount > 0 && (
              <span className="rounded-full bg-indigo-600 px-2 py-0.5 text-xs font-semibold text-white">
                {unclaimedInboxCount} unclaimed
              </span>
            )}
          </h2>
          <p className="mt-0.5 text-sm text-indigo-700">
            {unclaimedInboxCount === 0 ? "You're all caught up." : 'Live queue of items waiting on staff action.'}
          </p>
        </div>
        <span aria-hidden="true" className="shrink-0 text-indigo-600">
          →
        </span>
      </Link>

      <TodaySessionsSection clinicId={clinicId} sessions={todaySessions} totalCount={todaySessionCount} />

      <TodayStatsTile stats={todayStats} />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {visibleLinks.map((link) => (
          <Link
            key={link.title}
            to={link.path(clinicId)}
            className="block rounded-xl focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            <Card className="flex flex-col gap-3 hover:border-indigo-300">
              <div className="flex items-start justify-between gap-2">
                <IconBadge>{link.icon}</IconBadge>
                {link.title === 'Day sheet' && <CountBadge count={todaySessionCount} singular="session today" plural="sessions today" />}
                {link.title === 'Join a patient to the waitlist' && (
                  <CountBadge count={waitingCount} singular="waiting" plural="waiting" />
                )}
              </div>
              <div>
                <h3 className="text-sm font-semibold text-gray-900">{link.title}</h3>
                <p className="mt-1 text-sm text-gray-600">{link.description}</p>
              </div>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}
