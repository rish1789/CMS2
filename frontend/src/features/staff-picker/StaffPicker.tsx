import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { listClinicStaff, type StaffSummary } from './api'
import { EmployeeModal } from './EmployeeModal'
import { loadStaffSession } from '../staff-login/token'
import { ListSkeleton } from '../../components/ListSkeleton'
import { RoleBadge, type StaffRole } from '../../components/RoleBadge'
import { FilterSelect } from '../../components/FilterSelect'
import { avatarGradientClass } from '../../components/avatarGradient'
import { PaginationControls } from '../../components/PaginationControls'

const PAGE_SIZE = 10
const SEARCH_DEBOUNCE_MS = 300
const ROLE_FILTERS: Array<StaffRole | 'All'> = ['All', 'ClinicAdmin', 'Doctor', 'Operations']

type SortKey = 'name' | 'experienceYears' | 'joinedAt'
type SortDirection = 'asc' | 'desc'

function SortHeader({
  label,
  sortKey,
  activeKey,
  direction,
  onSort,
}: {
  label: string
  sortKey: SortKey
  activeKey: SortKey | null
  direction: SortDirection
  onSort: (key: SortKey) => void
}) {
  const isActive = activeKey === sortKey
  return (
    <th scope="col" className="px-4 py-3 font-semibold">
      <button
        type="button"
        onClick={() => onSort(sortKey)}
        aria-label={`Sort by ${label}`}
        className="flex items-center gap-1 text-gray-500 transition-colors duration-150 hover:text-gray-900"
      >
        {label}
        <span aria-hidden="true" className={`text-[10px] ${isActive ? 'text-indigo-600' : 'text-gray-300'}`}>
          {isActive && direction === 'desc' ? '▼' : '▲'}
        </span>
      </button>
    </th>
  )
}

// pagination-unification-2026-09-10: search/role/status/specialization filtering, sorting, and
// paging all now happen server-side (see staff-picker/api.ts) - this page used to fetch the
// clinic's entire roster once and do all of it in memory, behind a UI (Prev/Next/Jump-to-page,
// a record-range count) that already looked server-paginated.
export function StaffPicker() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [staff, setStaff] = useState<StaffSummary[] | null>(null)
  const [totalCount, setTotalCount] = useState(0)
  const [specializations, setSpecializations] = useState<string[]>([])
  const [error, setError] = useState<string | null>(null)
  const [searchInput, setSearchInput] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [roleFilter, setRoleFilter] = useState<StaffRole | 'All'>('All')
  const [specializationFilter, setSpecializationFilter] = useState('All')
  const [statusFilter, setStatusFilter] = useState<'Active' | 'Inactive' | 'All'>('Active')
  const [sortKey, setSortKey] = useState<SortKey | null>(null)
  const [sortDirection, setSortDirection] = useState<SortDirection>('asc')
  const [page, setPage] = useState(0)
  const [selectedMemberId, setSelectedMemberId] = useState<string | null>(null)
  const session = loadStaffSession()

  // Looked up from the current page's `staff` - a modal target is always a currently-rendered row.
  const selectedMember = staff?.find((member) => member.roleAssignmentId === selectedMemberId) ?? null

  // Debounce free-text search only - the discrete filter/sort controls below don't need it,
  // only keystroke-driven typing does. Mirrors PatientPicker's identical 300ms debounce.
  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(searchInput), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput])

  // Reset to page 0 whenever the query itself changes shape - adjusting state during render
  // (React's documented alternative to an effect for "derived from a prop/state change"), same
  // pattern this page used before pagination moved server-side.
  const queryKey = `${searchTerm}|${roleFilter}|${specializationFilter}|${statusFilter}|${sortKey}|${sortDirection}`
  const [prevQueryKey, setPrevQueryKey] = useState(queryKey)
  if (queryKey !== prevQueryKey) {
    setPrevQueryKey(queryKey)
    if (page !== 0) setPage(0)
  }

  // Patched in place, not re-fetched - the modal reads `member` from this same `staff` array,
  // so an eager background re-fetch (which would exclude the row once it fails the default
  // Active filter) can race the modal's own "already inactive" confirmation and yank it away
  // before the user sees it. The row simply keeps showing (now tagged "(Inactive)") until the
  // next real re-fetch (a filter/sort/page/search change) - the same tradeoff every optimistic
  // update in a server-paginated table makes.
  function handleDeactivated(roleAssignmentId: string) {
    setStaff((prev) =>
      prev
        ? prev.map((member) => (member.roleAssignmentId === roleAssignmentId ? { ...member, active: false } : member))
        : prev,
    )
  }

  useEffect(() => {
    if (!clinicId) return
    const activeSession = loadStaffSession()
    if (!activeSession) return
    let cancelled = false
    setStaff(null)
    listClinicStaff(clinicId, activeSession.token, {
      q: searchTerm.trim() || undefined,
      role: roleFilter === 'All' ? undefined : roleFilter,
      active: statusFilter === 'All' ? undefined : statusFilter === 'Active',
      specialization: specializationFilter === 'All' ? undefined : specializationFilter,
      sortBy: sortKey ?? undefined,
      sortDir: sortDirection,
      page,
      size: PAGE_SIZE,
    })
      .then((result) => {
        if (cancelled) return
        setStaff(result.staff)
        setTotalCount(result.totalCount)
        setSpecializations(result.specializations)
      })
      .catch(() => {
        if (!cancelled) setError('Failed to load staff.')
      })
    return () => {
      cancelled = true
    }
  }, [clinicId, searchTerm, roleFilter, specializationFilter, statusFilter, sortKey, sortDirection, page])

  function handleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDirection((direction) => (direction === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortKey(key)
      setSortDirection('asc')
    }
  }

  if (!clinicId) return null

  return (
    <div className="space-y-4">
      <h1 className="text-lg font-semibold text-gray-900">Roster</h1>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {staff === null && !error && <ListSkeleton rows={3} />}

      <div className="flex flex-wrap items-center gap-3">
        <input
          type="search"
          value={searchInput}
          onChange={(event) => setSearchInput(event.target.value)}
          placeholder="Search by name or staff code"
          aria-label="Search staff by name or staff code"
          className="min-w-0 flex-1 rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
        />
        <FilterSelect
          value={roleFilter}
          onChange={(value) => setRoleFilter(value as StaffRole | 'All')}
          ariaLabel="Filter staff by role"
        >
          {ROLE_FILTERS.map((role) => (
            <option key={role} value={role}>
              {role === 'All' ? 'All roles' : role}
            </option>
          ))}
        </FilterSelect>
        <FilterSelect
          value={statusFilter}
          onChange={(value) => setStatusFilter(value as 'Active' | 'Inactive' | 'All')}
          ariaLabel="Filter staff by status"
        >
          <option value="Active">Active</option>
          <option value="Inactive">Inactive</option>
          <option value="All">All statuses</option>
        </FilterSelect>
        {specializations.length > 0 && (
          <FilterSelect
            value={specializationFilter}
            onChange={setSpecializationFilter}
            ariaLabel="Filter staff by specialization"
          >
            <option value="All">All specializations</option>
            {specializations.map((specialization) => (
              <option key={specialization} value={specialization}>
                {specialization}
              </option>
            ))}
          </FilterSelect>
        )}
      </div>

      {staff && staff.length === 0 && (
        <p className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          No staff match your search.
        </p>
      )}

      {staff && staff.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="w-full min-w-[720px] text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
                  <SortHeader
                    label="Name"
                    sortKey="name"
                    activeKey={sortKey}
                    direction={sortDirection}
                    onSort={handleSort}
                  />
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Staff code
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Role
                  </th>
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Specialization
                  </th>
                  <SortHeader
                    label="Experience"
                    sortKey="experienceYears"
                    activeKey={sortKey}
                    direction={sortDirection}
                    onSort={handleSort}
                  />
                  <SortHeader
                    label="Joined"
                    sortKey="joinedAt"
                    activeKey={sortKey}
                    direction={sortDirection}
                    onSort={handleSort}
                  />
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {staff.map((member) => {
                  const isSelf = member.accountId === session?.accountId
                  return (
                    <tr key={member.roleAssignmentId} className="transition-colors duration-150 hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <button
                          type="button"
                          onClick={() => setSelectedMemberId(member.roleAssignmentId)}
                          className="flex items-center gap-3 rounded-md text-left focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-400"
                        >
                          <span
                            aria-hidden="true"
                            className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-xs font-semibold text-white shadow-sm ${avatarGradientClass(member.name)}`}
                          >
                            {member.name.charAt(0).toUpperCase()}
                          </span>
                          <span className="font-medium text-gray-900">
                            {member.name}
                            {isSelf && <span className="ml-1.5 text-xs font-normal text-gray-400">(You)</span>}
                            {!member.active && (
                              <span className="ml-1.5 text-xs font-normal text-gray-400">(Inactive)</span>
                            )}
                          </span>
                        </button>
                      </td>
                      <td className="px-4 py-3 text-gray-600">{member.staffCode}</td>
                      <td className="px-4 py-3">
                        <RoleBadge role={member.role} />
                      </td>
                      <td className="px-4 py-3 text-gray-600">{member.specialization ?? '—'}</td>
                      <td className="px-4 py-3 text-gray-600">
                        {member.experienceYears ?? '—'}
                      </td>
                      <td className="px-4 py-3 text-gray-600">
                        {new Date(member.joinedAt).toLocaleDateString(undefined, {
                          year: 'numeric',
                          month: 'short',
                          day: 'numeric',
                        })}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <PaginationControls page={page} pageSize={PAGE_SIZE} totalCount={totalCount} onPageChange={setPage} itemLabel="Records" />
        </>
      )}

      {selectedMember && clinicId && (
        <EmployeeModal
          member={selectedMember}
          clinicId={clinicId}
          isSelf={selectedMember.accountId === session?.accountId}
          onClose={() => setSelectedMemberId(null)}
          onDeactivated={handleDeactivated}
        />
      )}
    </div>
  )
}
