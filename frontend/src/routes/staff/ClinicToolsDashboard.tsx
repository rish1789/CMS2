import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import type { ReactNode } from 'react'
import { loadStaffSession } from '../../features/staff-login/token'
import { listSessions } from '../../features/day-sheet/api'
import { listInboxItems } from '../../features/inbox/api'
import { getWaitlistCount } from '../../features/waitlist/api'
import { IconBadge } from '../../components/adminIcons'
import {
  ClockIcon,
  DaySheetIcon,
  InboxIcon,
  SearchIcon,
  StethoscopeIcon,
  TeamIcon,
  UserPlusIcon,
} from '../../components/staffIcons'

// 041-staff-console-pickers: replaces most of the prior "type a raw ID, click Go" launcher
// grid with links into real browse/pick views. Only two tools that never had a typed-ID field
// remain as plain links here ("Onboard staff", "Join a patient to the waitlist") - everything
// else now starts from the Day Sheet, Doctor picker, Staff picker, or Patient search.

interface QuickLink {
  title: string
  description: string
  path: (clinicId: string) => string
  icon: ReactNode
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
  },
  {
    title: 'Join a patient to the waitlist',
    description: 'Add a patient to the waitlist for a doctor/specialization.',
    path: (clinicId) => `/staff/clinics/${clinicId}/waitlist/join`,
    icon: <ClockIcon />,
  },
]

function todayIsoDate(): string {
  const now = new Date()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

function CountBadge({ count, singular, plural }: { count: number | null; singular: string; plural: string }) {
  if (count === null) {
    return (
      <span aria-hidden="true" className="h-4 w-16 animate-pulse rounded-full bg-gray-100" />
    )
  }
  return (
    <span className="rounded-full bg-gray-100 px-2 py-0.5 text-xs font-medium text-gray-600">
      {count} {count === 1 ? singular : plural}
    </span>
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
  const [unclaimedInboxCount, setUnclaimedInboxCount] = useState<number | null>(null)
  const [waitingCount, setWaitingCount] = useState<number | null>(null)

  useEffect(() => {
    if (!clinicId) return
    const session = loadStaffSession()
    if (!session) return
    const today = todayIsoDate()

    listSessions(clinicId, session.token, { from: today, to: today, page: 0, size: 1 })
      .then((result) => setTodaySessionCount(result.totalCount))
      .catch(() => {})

    listInboxItems(clinicId, session.token)
      .then((items) => setUnclaimedInboxCount(items.filter((item) => item.status === 'UNCLAIMED').length))
      .catch(() => {})

    getWaitlistCount(clinicId, session.token)
      .then((result) => setWaitingCount(result.waitingCount))
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

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {LINKS.map((link) => (
          <Link
            key={link.title}
            to={link.path(clinicId)}
            // staff-console-audit-2026-09-10 P3: was hover:shadow-sm on a card already shadow-sm
            // - a visual no-op. hover:shadow-md actually changes on hover.
            className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
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
          </Link>
        ))}
      </div>
    </div>
  )
}
