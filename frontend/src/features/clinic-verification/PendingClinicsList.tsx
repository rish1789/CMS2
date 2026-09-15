import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  listClinics,
  verifyClinic,
  unverifyClinic,
  rejectClinic,
  rejectClinicsBulk,
  restoreClinic,
  deleteClinic,
  deleteClinicsBulk,
  AdminApiError,
  type ClinicListStatus,
  type ClinicSortField,
  type ClinicSummary,
  type SortDirection,
} from './api'
import { loadSuperAdminSession, storeSuperAdminSession } from '../super-admin/token'
import { PaginationControls } from '../../components/PaginationControls'
import { ListSkeleton } from '../../components/ListSkeleton'
import { RejectConfirmModal } from '../../components/RejectConfirmModal'
import { DeleteConfirmModal } from '../../components/DeleteConfirmModal'
import { SortableColumnHeader } from '../../components/SortableColumnHeader'
import { REJECTION_REASON_OPTIONS, type RejectionReason } from '../../components/rejectionReason'
import { ClinicIcon, IconBadge } from '../../components/adminIcons'

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

function reasonLabel(reason: RejectionReason | null): string {
  return REJECTION_REASON_OPTIONS.find((option) => option.value === reason)?.label ?? '—'
}

type Tab = 'pending' | 'verified' | 'rejected'

const TAB_STATUS: Record<Tab, ClinicListStatus> = { pending: 'PENDING', verified: 'VERIFIED', rejected: 'REJECTED' }

const CLINICS_PAGE_SIZE = 15
const SEARCH_DEBOUNCE_MS = 300
const DEFAULT_SORT_FIELD: ClinicSortField = 'createdAt'
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc'

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// clinic in this tab, reveal more client-side" pattern - the platform-wide pending-verification
// queue grows unbounded as more clinics register.
export function PendingClinicsList() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('pending')
  const [page, setPage] = useState(0)
  const [clinics, setClinics] = useState<ClinicSummary[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actioningId, setActioningId] = useState<string | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  // super-admin-console-redesign-2026-09-11: un-verifying cascades to auto-cancel this clinic's
  // future bookings (008-deverification-cascade) - the highest-stakes single click in the console,
  // so it gets the same idle/confirming/submitting guard as CancelSessionButton.
  const [confirmingActionId, setConfirmingActionId] = useState<string | null>(null)
  // super-admin-console-redesign-2026-09-11: bulk selection for Reject (Pending) / permanent
  // Delete (Rejected) - cleared on every tab/page change so a selection never silently carries
  // across an unrelated view.
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [rejectItems, setRejectItems] = useState<{ id: string; label: string }[] | null>(null)
  const [deleteItems, setDeleteItems] = useState<{ id: string; label: string }[] | null>(null)

  // stress-test-2026-09-11: server-side search/sort/reason-filter, not a client-side filter over
  // a downloaded page - the queue is expected to hold hundreds to thousands of clinics.
  const [searchInput, setSearchInput] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [reasonFilter, setReasonFilter] = useState<RejectionReason | ''>('')
  const [sortField, setSortField] = useState<ClinicSortField>(DEFAULT_SORT_FIELD)
  const [sortDirection, setSortDirection] = useState<SortDirection>(DEFAULT_SORT_DIRECTION)

  // Debounce free-text search only - see DoctorPicker's identical 300ms debounce.
  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(searchInput), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput])

  // 040-super-admin-rbac-login: route entry is already gated by RequireSuperAdminSession,
  // so a session is expected here - this only re-checks for the case where it expired or
  // was cleared mid-visit (US3 Acceptance Scenario 4).
  function handleUnauthorized() {
    storeSuperAdminSession(null)
    navigate('/staff/login', { replace: true })
  }

  useEffect(() => {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    let cancelled = false
    setLoading(true)
    setError(null)

    listClinics(TAB_STATUS[tab], session.token, {
      page,
      size: CLINICS_PAGE_SIZE,
      q: searchTerm.trim() || undefined,
      reason: tab === 'rejected' && reasonFilter ? reasonFilter : undefined,
      sort: sortField,
      direction: sortDirection,
    })
      .then((result) => {
        if (cancelled) return
        setClinics(result.clinics)
        setTotalCount(result.totalCount)
        // An action (verify/un-verify/reject/delete) moves a clinic off this tab server-side; if
        // that emptied an otherwise-non-first page, step back one page rather than a dead end.
        if (result.clinics.length === 0 && page > 0) setPage((current) => current - 1)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof AdminApiError && err.status === 401) {
          handleUnauthorized()
        } else {
          setError(err instanceof Error ? err.message : 'Failed to load clinics.')
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tab, page, reloadToken, searchTerm, reasonFilter, sortField, sortDirection])

  function handleTabChange(newTab: Tab) {
    setTab(newTab)
    setPage(0)
    setSelectedIds(new Set())
    setReasonFilter('')
    setSortField(DEFAULT_SORT_FIELD)
    setSortDirection(DEFAULT_SORT_DIRECTION)
  }

  function handlePageChange(newPage: number) {
    setPage(newPage)
    setSelectedIds(new Set())
  }

  function handleSearchChange(value: string) {
    setSearchInput(value)
    setPage(0)
    setSelectedIds(new Set())
  }

  function handleReasonFilterChange(value: RejectionReason | '') {
    setReasonFilter(value)
    setPage(0)
    setSelectedIds(new Set())
  }

  function handleSort(field: ClinicSortField) {
    if (field === sortField) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortField(field)
      setSortDirection('asc')
    }
    setPage(0)
  }

  async function handleAction(clinic: ClinicSummary) {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setActioningId(clinic.clinicId)
    setError(null)
    try {
      if (tab === 'pending') {
        await verifyClinic(clinic.clinicId, session.token)
      } else {
        await unverifyClinic(clinic.clinicId, session.token)
      }
      // The clinic moved to the other tab server-side - re-fetch this page rather than just
      // filtering it out locally, since the correct next item to show (if any) now lives one
      // slot further along the server-side result set.
      setConfirmingActionId(null)
      setReloadToken((current) => current + 1)
    } catch (err) {
      if (err instanceof AdminApiError && err.status === 401) {
        handleUnauthorized()
      } else {
        setError(err instanceof Error ? err.message : 'Action failed.')
      }
    } finally {
      setActioningId(null)
    }
  }

  async function handleRestore(clinic: ClinicSummary) {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setActioningId(clinic.clinicId)
    setError(null)
    try {
      await restoreClinic(clinic.clinicId, session.token)
      setReloadToken((current) => current + 1)
    } catch (err) {
      if (err instanceof AdminApiError && err.status === 401) {
        handleUnauthorized()
      } else {
        setError(err instanceof Error ? err.message : 'Restore failed.')
      }
    } finally {
      setActioningId(null)
    }
  }

  function toggleSelected(clinicId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(clinicId)) next.delete(clinicId)
      else next.add(clinicId)
      return next
    })
  }

  function toggleSelectAll() {
    setSelectedIds((prev) =>
      prev.size === clinics.length ? new Set() : new Set(clinics.map((clinic) => clinic.clinicId)),
    )
  }

  // super-admin-console-redesign-2026-09-11: single and bulk reject share one modal and one
  // submit handler - the only difference is how many ids are in `rejectItems`.
  async function handleRejectSubmit(reasonCode: RejectionReason, detail: string) {
    const session = loadSuperAdminSession()
    if (!session || !rejectItems) return
    try {
      if (rejectItems.length === 1) {
        await rejectClinic(rejectItems[0].id, reasonCode, detail, session.token)
        setRejectItems(null)
      } else {
        const result = await rejectClinicsBulk(
          rejectItems.map((item) => item.id),
          reasonCode,
          detail,
          session.token,
        )
        const failedCount = Object.keys(result.failed).length
        if (failedCount > 0) {
          setSelectedIds(new Set())
          setReloadToken((current) => current + 1)
          throw new Error(
            `${result.succeeded.length} rejected, ${failedCount} could not be rejected (already verified or no longer pending).`,
          )
        }
        setRejectItems(null)
      }
      setSelectedIds(new Set())
      setReloadToken((current) => current + 1)
    } catch (err) {
      if (err instanceof AdminApiError && err.status === 401) {
        handleUnauthorized()
        return
      }
      throw err
    }
  }

  // super-admin-console-redesign-2026-09-11: single and bulk permanent-delete share one modal
  // and one submit handler, mirroring handleRejectSubmit's shape exactly.
  async function handleDeleteSubmit() {
    const session = loadSuperAdminSession()
    if (!session || !deleteItems) return
    try {
      if (deleteItems.length === 1) {
        await deleteClinic(deleteItems[0].id, session.token)
        setDeleteItems(null)
      } else {
        const result = await deleteClinicsBulk(
          deleteItems.map((item) => item.id),
          session.token,
        )
        const failedCount = Object.keys(result.failed).length
        if (failedCount > 0) {
          setSelectedIds(new Set())
          setReloadToken((current) => current + 1)
          throw new Error(
            `${result.succeeded.length} deleted, ${failedCount} could not be deleted (real activity is attached - see each record).`,
          )
        }
        setDeleteItems(null)
      }
      setSelectedIds(new Set())
      setReloadToken((current) => current + 1)
    } catch (err) {
      if (err instanceof AdminApiError && err.status === 401) {
        handleUnauthorized()
        return
      }
      throw err
    }
  }

  const allOnPageSelected = clinics.length > 0 && selectedIds.size === clinics.length
  const selectedClinics = clinics.filter((clinic) => selectedIds.has(clinic.clinicId))

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 border-b border-gray-100 pb-5">
        <IconBadge>
          <ClinicIcon />
        </IconBadge>
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Clinic verification</h1>
          <p className="mt-0.5 text-sm text-gray-600">
            Verify a newly registered clinic, un-verify one already on the platform, or reject a registration that
            isn't genuine.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Clinic verification status" className="flex gap-2">
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'pending'}
            onClick={() => handleTabChange('pending')}
            className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition-all duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${
              tab === 'pending'
                ? 'bg-indigo-600 text-white shadow-sm focus-visible:ring-indigo-500'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 focus-visible:ring-gray-400'
            }`}
          >
            Pending
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'verified'}
            onClick={() => handleTabChange('verified')}
            className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition-all duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${
              tab === 'verified'
                ? 'bg-indigo-600 text-white shadow-sm focus-visible:ring-indigo-500'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 focus-visible:ring-gray-400'
            }`}
          >
            Verified
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={tab === 'rejected'}
            onClick={() => handleTabChange('rejected')}
            className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition-all duration-150 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${
              tab === 'rejected'
                ? 'bg-indigo-600 text-white shadow-sm focus-visible:ring-indigo-500'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200 focus-visible:ring-gray-400'
            }`}
          >
            Rejected
          </button>
        </div>

        <div className="flex flex-wrap items-center gap-2">
          {tab === 'rejected' && (
            <select
              aria-label="Filter by rejection reason"
              value={reasonFilter}
              onChange={(event) => handleReasonFilterChange(event.target.value as RejectionReason | '')}
              className="input h-10 w-auto"
            >
              <option value="">All reasons</option>
              {REJECTION_REASON_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          )}
          <label htmlFor="clinic-search" className="sr-only">
            Search clinics
          </label>
          <input
            id="clinic-search"
            type="search"
            value={searchInput}
            onChange={(event) => handleSearchChange(event.target.value)}
            placeholder="Search by name, address, or contact"
            className="w-72 max-w-full rounded-lg border border-gray-300 px-3 py-2 text-sm text-gray-700 placeholder:text-gray-400 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
          />
        </div>
      </div>

      {error && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {error}
        </p>
      )}

      {(tab === 'pending' || tab === 'rejected') && selectedIds.size > 0 && (
        <div className="flex items-center justify-between rounded-lg border border-red-200 bg-red-50 px-4 py-2.5">
          <span className="text-sm font-medium text-red-800">{selectedIds.size} selected</span>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setSelectedIds(new Set())}
              className="rounded-lg px-3 py-1.5 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100"
            >
              Clear
            </button>
            {tab === 'pending' ? (
              <button
                type="button"
                onClick={() =>
                  setRejectItems(selectedClinics.map((clinic) => ({ id: clinic.clinicId, label: clinic.name })))
                }
                className="rounded-lg border border-red-300 bg-white px-3.5 py-1.5 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100"
              >
                Reject selected
              </button>
            ) : (
              <button
                type="button"
                onClick={() =>
                  setDeleteItems(selectedClinics.map((clinic) => ({ id: clinic.clinicId, label: clinic.name })))
                }
                className="rounded-lg bg-red-600 px-3.5 py-1.5 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-red-500"
              >
                Delete selected permanently
              </button>
            )}
          </div>
        </div>
      )}

      {loading && <ListSkeleton rows={4} />}

      {!loading && clinics.length === 0 && (
        <p className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          {searchTerm || reasonFilter ? 'No clinics match your search/filter.' : 'No clinics in this list.'}
        </p>
      )}

      {!loading && clinics.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="w-full min-w-[640px] text-left text-sm">
              <thead>
                <tr className="border-b border-gray-200 bg-gray-50 text-xs font-semibold uppercase tracking-wide text-gray-500">
                  {(tab === 'pending' || tab === 'rejected') && (
                    <th scope="col" className="w-10 px-4 py-3">
                      <input
                        type="checkbox"
                        aria-label="Select all"
                        checked={allOnPageSelected}
                        onChange={toggleSelectAll}
                        className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                      />
                    </th>
                  )}
                  <SortableColumnHeader
                    label="Clinic"
                    field="name"
                    currentSort={sortField}
                    currentDirection={sortDirection}
                    onSort={handleSort}
                  />
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Contact
                  </th>
                  {tab === 'rejected' ? (
                    <>
                      <th scope="col" className="px-4 py-3 font-semibold">
                        Reason
                      </th>
                      <SortableColumnHeader
                        label="Rejected"
                        field="rejectedAt"
                        currentSort={sortField}
                        currentDirection={sortDirection}
                        onSort={handleSort}
                      />
                    </>
                  ) : (
                    <SortableColumnHeader
                      label="Registered"
                      field="createdAt"
                      currentSort={sortField}
                      currentDirection={sortDirection}
                      onSort={handleSort}
                    />
                  )}
                  <th scope="col" className="px-4 py-3 font-semibold">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {clinics.map((clinic) => (
                  <tr key={clinic.clinicId} className="transition-colors duration-150 hover:bg-gray-50">
                    {(tab === 'pending' || tab === 'rejected') && (
                      <td className="px-4 py-3">
                        <input
                          type="checkbox"
                          aria-label={`Select ${clinic.name}`}
                          checked={selectedIds.has(clinic.clinicId)}
                          onChange={() => toggleSelected(clinic.clinicId)}
                          className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                        />
                      </td>
                    )}
                    <td className="px-4 py-3">
                      <p className="font-medium text-gray-900">{clinic.name}</p>
                      <p className="text-sm text-gray-600">{clinic.address}</p>
                    </td>
                    <td className="px-4 py-3 text-gray-600">
                      {clinic.contactEmail ?? clinic.contactMobile ?? '—'}
                    </td>
                    {tab === 'rejected' ? (
                      <>
                        <td className="px-4 py-3 text-gray-600">
                          <p>{reasonLabel(clinic.rejectionReason)}</p>
                          {clinic.rejectionDetail && (
                            <p className="text-xs text-gray-400">{clinic.rejectionDetail}</p>
                          )}
                        </td>
                        <td className="px-4 py-3 text-gray-600">
                          {clinic.rejectedAt ? formatDate(clinic.rejectedAt) : '—'}
                        </td>
                      </>
                    ) : (
                      <td className="px-4 py-3 text-gray-600">{formatDate(clinic.createdAt)}</td>
                    )}
                    <td className="px-4 py-3 text-right">
                      {tab === 'pending' &&
                        (confirmingActionId === null || confirmingActionId !== clinic.clinicId) && (
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => handleAction(clinic)}
                              disabled={actioningId === clinic.clinicId}
                              className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                            >
                              Verify
                            </button>
                            <button
                              type="button"
                              onClick={() => setRejectItems([{ id: clinic.clinicId, label: clinic.name }])}
                              className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                            >
                              Remove
                            </button>
                          </div>
                        )}
                      {tab === 'verified' &&
                        (confirmingActionId === clinic.clinicId ? (
                          <div className="flex flex-wrap items-center justify-end gap-2">
                            <span className="text-sm text-gray-700">Cancels its future bookings?</span>
                            <button
                              type="button"
                              onClick={() => handleAction(clinic)}
                              disabled={actioningId === clinic.clinicId}
                              className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                            >
                              {actioningId === clinic.clinicId ? 'Un-verifying…' : 'Confirm'}
                            </button>
                            <button
                              type="button"
                              onClick={() => setConfirmingActionId(null)}
                              disabled={actioningId === clinic.clinicId}
                              className="rounded-lg border border-gray-300 px-3.5 py-2 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                            >
                              Back
                            </button>
                          </div>
                        ) : (
                          <button
                            type="button"
                            onClick={() => setConfirmingActionId(clinic.clinicId)}
                            className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                          >
                            Un-verify
                          </button>
                        ))}
                      {tab === 'rejected' && (
                        <div className="flex justify-end gap-2">
                          <button
                            type="button"
                            onClick={() => handleRestore(clinic)}
                            disabled={actioningId === clinic.clinicId}
                            className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                          >
                            {actioningId === clinic.clinicId ? 'Restoring…' : 'Restore'}
                          </button>
                          <button
                            type="button"
                            onClick={() => setDeleteItems([{ id: clinic.clinicId, label: clinic.name }])}
                            className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                          >
                            Delete
                          </button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <PaginationControls
            page={page}
            pageSize={CLINICS_PAGE_SIZE}
            totalCount={totalCount}
            onPageChange={handlePageChange}
            itemLabel="clinics"
          />
        </>
      )}

      {rejectItems && (
        <RejectConfirmModal
          items={rejectItems}
          entityNoun="clinic"
          onClose={() => setRejectItems(null)}
          onSubmit={handleRejectSubmit}
        />
      )}

      {deleteItems && (
        <DeleteConfirmModal
          items={deleteItems}
          entityNoun="clinic"
          onClose={() => setDeleteItems(null)}
          onSubmit={handleDeleteSubmit}
        />
      )}
    </div>
  )
}
