import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  listDoctors,
  verifyDoctor,
  revokeDoctor,
  editDoctor,
  rejectDoctor,
  rejectDoctorsBulk,
  restoreDoctor,
  deleteDoctor,
  deleteDoctorsBulk,
  AdminApiError,
  type DoctorListStatus,
  type DoctorProfileSummary,
  type DoctorSortField,
  type EditDoctorRequest,
  type SortDirection,
} from './api'
import { loadSuperAdminSession, storeSuperAdminSession } from '../super-admin/token'
import { PaginationControls } from '../../components/PaginationControls'
import { ListSkeleton } from '../../components/ListSkeleton'
import { avatarGradientClass } from '../../components/avatarGradient'
import { RejectConfirmModal } from '../../components/RejectConfirmModal'
import { DeleteConfirmModal } from '../../components/DeleteConfirmModal'
import { SortableColumnHeader } from '../../components/SortableColumnHeader'
import { REJECTION_REASON_OPTIONS, type RejectionReason } from '../../components/rejectionReason'
import { DoctorIcon, IconBadge } from '../../components/adminIcons'

type Tab = 'pending' | 'verified' | 'rejected'

const TAB_STATUS: Record<Tab, DoctorListStatus> = { pending: 'PENDING', verified: 'VERIFIED', rejected: 'REJECTED' }

const DOCTORS_PAGE_SIZE = 15
const SEARCH_DEBOUNCE_MS = 300
const DEFAULT_SORT_FIELD: DoctorSortField = 'createdAt'
const DEFAULT_SORT_DIRECTION: SortDirection = 'desc'

function toEditForm(doctor: DoctorProfileSummary): EditDoctorRequest {
  return {
    specialization: doctor.specialization,
    licenseNumber: doctor.licenseNumber,
    experienceYears: doctor.experienceYears,
    visible: doctor.visible,
  }
}

function reasonLabel(reason: RejectionReason | null): string {
  return REJECTION_REASON_OPTIONS.find((option) => option.value === reason)?.label ?? '—'
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}

// pagination-unification-2026-09-10: real server-side pagination, replacing the "fetch every
// doctor in this tab, reveal more client-side" pattern - the platform-wide license-verification
// queue grows unbounded as more doctors onboard.
export function PendingDoctorsList() {
  const navigate = useNavigate()
  const [tab, setTab] = useState<Tab>('pending')
  const [page, setPage] = useState(0)
  const [doctors, setDoctors] = useState<DoctorProfileSummary[]>([])
  const [totalCount, setTotalCount] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [actioningId, setActioningId] = useState<string | null>(null)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editForm, setEditForm] = useState<EditDoctorRequest | null>(null)
  const [reloadToken, setReloadToken] = useState(0)
  // super-admin-console-redesign-2026-09-11: revoking un-verifies a doctor's license outright -
  // same idle/confirming/submitting shape as CancelSessionButton, so a single misclick can never
  // do it.
  const [confirmingRevokeId, setConfirmingRevokeId] = useState<string | null>(null)
  // Bulk selection for Reject (Pending) / permanent Delete (Rejected) - cleared on every
  // tab/page change so a selection never silently carries across an unrelated view.
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())
  const [rejectItems, setRejectItems] = useState<{ id: string; label: string }[] | null>(null)
  const [deleteItems, setDeleteItems] = useState<{ id: string; label: string }[] | null>(null)

  // stress-test-2026-09-11: server-side search/sort/reason-filter, not a client-side filter over
  // a downloaded page - the queue is expected to hold hundreds to thousands of doctors.
  const [searchInput, setSearchInput] = useState('')
  const [searchTerm, setSearchTerm] = useState('')
  const [reasonFilter, setReasonFilter] = useState<RejectionReason | ''>('')
  const [sortField, setSortField] = useState<DoctorSortField>(DEFAULT_SORT_FIELD)
  const [sortDirection, setSortDirection] = useState<SortDirection>(DEFAULT_SORT_DIRECTION)

  // Debounce free-text search only - see DoctorPicker's identical 300ms debounce.
  useEffect(() => {
    const timer = setTimeout(() => setSearchTerm(searchInput), SEARCH_DEBOUNCE_MS)
    return () => clearTimeout(timer)
  }, [searchInput])

  // 040-super-admin-rbac-login: route entry is already gated by RequireSuperAdminSession;
  // this only re-checks for the case where it expired or was cleared mid-visit.
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

    listDoctors(TAB_STATUS[tab], session.token, {
      page,
      size: DOCTORS_PAGE_SIZE,
      q: searchTerm.trim() || undefined,
      reason: tab === 'rejected' && reasonFilter ? reasonFilter : undefined,
      sort: sortField,
      direction: sortDirection,
    })
      .then((result) => {
        if (cancelled) return
        setDoctors(result.doctors)
        setTotalCount(result.totalCount)
        // An action (verify/revoke/reject/delete/a verification-resetting edit) moves a doctor
        // off this tab server-side; if that emptied an otherwise-non-first page, step back
        // rather than showing a dead end.
        if (result.doctors.length === 0 && page > 0) setPage((current) => current - 1)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof AdminApiError && err.status === 401) {
          handleUnauthorized()
        } else {
          setError(err instanceof Error ? err.message : 'Failed to load doctors.')
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

  function handleSort(field: DoctorSortField) {
    if (field === sortField) {
      setSortDirection((current) => (current === 'asc' ? 'desc' : 'asc'))
    } else {
      setSortField(field)
      setSortDirection('asc')
    }
    setPage(0)
  }

  async function handleVerify(doctor: DoctorProfileSummary) {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setActioningId(doctor.doctorProfileId)
    setError(null)
    try {
      await verifyDoctor(doctor.doctorProfileId, session.token)
      // Moved to the other tab server-side - re-fetch this page rather than filtering locally.
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

  async function handleRevoke(doctor: DoctorProfileSummary) {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setActioningId(doctor.doctorProfileId)
    setError(null)
    try {
      await revokeDoctor(doctor.doctorProfileId, session.token)
      setConfirmingRevokeId(null)
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

  async function handleRestore(doctor: DoctorProfileSummary) {
    const session = loadSuperAdminSession()
    if (!session) {
      handleUnauthorized()
      return
    }
    setActioningId(doctor.doctorProfileId)
    setError(null)
    try {
      await restoreDoctor(doctor.doctorProfileId, session.token)
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

  function handleStartEdit(doctor: DoctorProfileSummary) {
    setError(null)
    setEditingId(doctor.doctorProfileId)
    setEditForm(toEditForm(doctor))
  }

  function handleCancelEdit() {
    setEditingId(null)
    setEditForm(null)
  }

  async function handleSaveEdit(doctorProfileId: string) {
    const session = loadSuperAdminSession()
    if (!session || !editForm) return
    setActioningId(doctorProfileId)
    setError(null)
    try {
      const updated = await editDoctor(doctorProfileId, editForm, session.token)
      // 008: a license-number edit may reset licenseVerified, which can move the row out
      // of whichever tab is currently showing it (e.g. a reset drops it off the Verified
      // tab) - re-fetch in that case rather than just filtering it out of a now-stale page;
      // otherwise a simple in-place update is enough (no server-side membership change).
      const stillBelongsInCurrentTab = updated.licenseVerified === (tab === 'verified')
      if (stillBelongsInCurrentTab) {
        setDoctors((prev) => prev.map((d) => (d.doctorProfileId === doctorProfileId ? updated : d)))
      } else {
        setReloadToken((current) => current + 1)
      }
      setEditingId(null)
      setEditForm(null)
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

  function toggleSelected(doctorProfileId: string) {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(doctorProfileId)) next.delete(doctorProfileId)
      else next.add(doctorProfileId)
      return next
    })
  }

  function toggleSelectAll() {
    setSelectedIds((prev) =>
      prev.size === doctors.length ? new Set() : new Set(doctors.map((doctor) => doctor.doctorProfileId)),
    )
  }

  // super-admin-console-redesign-2026-09-11: single and bulk reject share one modal and one
  // submit handler - the only difference is how many ids are in `rejectItems`.
  async function handleRejectSubmit(reasonCode: RejectionReason, detail: string) {
    const session = loadSuperAdminSession()
    if (!session || !rejectItems) return
    try {
      if (rejectItems.length === 1) {
        await rejectDoctor(rejectItems[0].id, reasonCode, detail, session.token)
        setRejectItems(null)
      } else {
        const result = await rejectDoctorsBulk(
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
        await deleteDoctor(deleteItems[0].id, session.token)
        setDeleteItems(null)
      } else {
        const result = await deleteDoctorsBulk(
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

  const allOnPageSelected = doctors.length > 0 && selectedIds.size === doctors.length
  const columnCount = tab === 'pending' || tab === 'rejected' ? 6 : 5
  const selectedDoctors = doctors.filter((doctor) => selectedIds.has(doctor.doctorProfileId))

  return (
    <div className="space-y-6">
      <div className="flex items-start gap-3 border-b border-gray-100 pb-5">
        <IconBadge>
          <DoctorIcon />
        </IconBadge>
        <div>
          <h1 className="text-lg font-semibold text-gray-900">Doctor license verification</h1>
          <p className="mt-0.5 text-sm text-gray-600">
            Review and verify a doctor's medical license before they appear in public discovery, or reject a
            submission that isn't genuine.
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <div role="tablist" aria-label="Doctor license verification status" className="flex gap-2">
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
          <label htmlFor="doctor-search" className="sr-only">
            Search doctors
          </label>
          <input
            id="doctor-search"
            type="search"
            value={searchInput}
            onChange={(event) => handleSearchChange(event.target.value)}
            placeholder="Search by name, email, specialization, or license"
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
                  setRejectItems(
                    selectedDoctors.map((doctor) => ({ id: doctor.doctorProfileId, label: doctor.accountName })),
                  )
                }
                className="rounded-lg border border-red-300 bg-white px-3.5 py-1.5 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-100"
              >
                Reject selected
              </button>
            ) : (
              <button
                type="button"
                onClick={() =>
                  setDeleteItems(
                    selectedDoctors.map((doctor) => ({ id: doctor.doctorProfileId, label: doctor.accountName })),
                  )
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

      {!loading && doctors.length === 0 && (
        <p className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 shadow-sm">
          {searchTerm || reasonFilter ? 'No doctors match your search/filter.' : 'No doctors in this list.'}
        </p>
      )}

      {!loading && doctors.length > 0 && (
        <>
          <div className="overflow-x-auto rounded-xl border border-gray-200 bg-white shadow-sm">
            <table className="w-full min-w-[720px] text-left text-sm">
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
                  <th scope="col" className="px-4 py-3 font-semibold">
                    Doctor
                  </th>
                  <SortableColumnHeader
                    label="Specialization"
                    field="specialization"
                    currentSort={sortField}
                    currentDirection={sortDirection}
                    onSort={handleSort}
                  />
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
                    <>
                      <SortableColumnHeader
                        label="License"
                        field="licenseNumber"
                        currentSort={sortField}
                        currentDirection={sortDirection}
                        onSort={handleSort}
                      />
                      <SortableColumnHeader
                        label="Experience"
                        field="experienceYears"
                        currentSort={sortField}
                        currentDirection={sortDirection}
                        onSort={handleSort}
                      />
                    </>
                  )}
                  <th scope="col" className="px-4 py-3 font-semibold">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {doctors.map((doctor) =>
                  editingId === doctor.doctorProfileId && editForm ? (
                    <tr key={doctor.doctorProfileId} className="bg-indigo-50/40">
                      <td colSpan={columnCount} className="p-4">
                        <div className="space-y-3 rounded-lg border border-indigo-200 bg-white p-4 shadow-sm">
                          <p className="text-sm font-semibold text-gray-900">Editing {doctor.accountName}</p>
                          <div className="grid gap-3 sm:grid-cols-2">
                            <div>
                              <label
                                htmlFor={`edit-specialization-${doctor.doctorProfileId}`}
                                className="block text-sm font-medium text-gray-700"
                              >
                                Specialization
                              </label>
                              <input
                                id={`edit-specialization-${doctor.doctorProfileId}`}
                                value={editForm.specialization}
                                onChange={(e) => setEditForm({ ...editForm, specialization: e.target.value })}
                                className="input mt-1"
                              />
                            </div>
                            <div>
                              <label
                                htmlFor={`edit-license-${doctor.doctorProfileId}`}
                                className="block text-sm font-medium text-gray-700"
                              >
                                License number
                              </label>
                              <input
                                id={`edit-license-${doctor.doctorProfileId}`}
                                value={editForm.licenseNumber}
                                onChange={(e) => setEditForm({ ...editForm, licenseNumber: e.target.value })}
                                className="input mt-1"
                              />
                            </div>
                          </div>
                          <div>
                            <label
                              htmlFor={`edit-experience-${doctor.doctorProfileId}`}
                              className="block text-sm font-medium text-gray-700"
                            >
                              Experience (years)
                            </label>
                            <input
                              id={`edit-experience-${doctor.doctorProfileId}`}
                              type="number"
                              min={0}
                              value={editForm.experienceYears}
                              onChange={(e) => setEditForm({ ...editForm, experienceYears: Number(e.target.value) })}
                              className="input mt-1 max-w-xs"
                            />
                          </div>
                          <div className="flex items-center gap-2">
                            <input
                              id={`edit-visible-${doctor.doctorProfileId}`}
                              type="checkbox"
                              checked={editForm.visible}
                              onChange={(e) => setEditForm({ ...editForm, visible: e.target.checked })}
                            />
                            <label htmlFor={`edit-visible-${doctor.doctorProfileId}`} className="text-sm text-gray-700">
                              Visible in public discovery
                            </label>
                          </div>
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={handleCancelEdit}
                              className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                            >
                              Cancel
                            </button>
                            <button
                              type="button"
                              onClick={() => handleSaveEdit(doctor.doctorProfileId)}
                              disabled={actioningId === doctor.doctorProfileId}
                              className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                            >
                              Save
                            </button>
                          </div>
                        </div>
                      </td>
                    </tr>
                  ) : (
                    <tr key={doctor.doctorProfileId} className="transition-colors duration-150 hover:bg-gray-50">
                      {(tab === 'pending' || tab === 'rejected') && (
                        <td className="px-4 py-3">
                          <input
                            type="checkbox"
                            aria-label={`Select ${doctor.accountName}`}
                            checked={selectedIds.has(doctor.doctorProfileId)}
                            onChange={() => toggleSelected(doctor.doctorProfileId)}
                            className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-500"
                          />
                        </td>
                      )}
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <span
                            aria-hidden="true"
                            className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gradient-to-br text-xs font-semibold text-white shadow-sm ${avatarGradientClass(doctor.accountName)}`}
                          >
                            {doctor.accountName.charAt(0).toUpperCase()}
                          </span>
                          <div>
                            <p className="font-medium text-gray-900">{doctor.accountName}</p>
                            <p className="text-xs text-gray-500">{doctor.accountEmail}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <span className="rounded-full bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700">
                          {doctor.specialization}
                        </span>
                      </td>
                      {tab === 'rejected' ? (
                        <>
                          <td className="px-4 py-3 text-gray-600">
                            <p>{reasonLabel(doctor.rejectionReason)}</p>
                            {doctor.rejectionDetail && (
                              <p className="text-xs text-gray-400">{doctor.rejectionDetail}</p>
                            )}
                          </td>
                          <td className="px-4 py-3 text-gray-600">
                            {doctor.rejectedAt ? formatDate(doctor.rejectedAt) : '—'}
                          </td>
                        </>
                      ) : (
                        <>
                          <td className="px-4 py-3 text-gray-600">{doctor.licenseNumber}</td>
                          <td className="px-4 py-3 text-gray-600">{doctor.experienceYears} yrs</td>
                        </>
                      )}
                      <td className="px-4 py-3 text-right">
                        {tab === 'rejected' && (
                          <div className="flex justify-end gap-2">
                            <button
                              type="button"
                              onClick={() => handleRestore(doctor)}
                              disabled={actioningId === doctor.doctorProfileId}
                              className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                            >
                              {actioningId === doctor.doctorProfileId ? 'Restoring…' : 'Restore'}
                            </button>
                            <button
                              type="button"
                              onClick={() =>
                                setDeleteItems([{ id: doctor.doctorProfileId, label: doctor.accountName }])
                              }
                              className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                            >
                              Delete
                            </button>
                          </div>
                        )}
                        {tab !== 'rejected' &&
                          (confirmingRevokeId === doctor.doctorProfileId ? (
                            <div className="flex flex-wrap items-center justify-end gap-2">
                              <span className="text-sm text-gray-700">Revoke license?</span>
                              <button
                                type="button"
                                onClick={() => handleRevoke(doctor)}
                                disabled={actioningId === doctor.doctorProfileId}
                                className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                              >
                                {actioningId === doctor.doctorProfileId ? 'Revoking…' : 'Confirm'}
                              </button>
                              <button
                                type="button"
                                onClick={() => setConfirmingRevokeId(null)}
                                disabled={actioningId === doctor.doctorProfileId}
                                className="rounded-lg border border-gray-300 px-3.5 py-2 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50 disabled:opacity-50 disabled:pointer-events-none focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                              >
                                Back
                              </button>
                            </div>
                          ) : (
                            <div className="flex justify-end gap-2">
                              <button
                                type="button"
                                onClick={() => handleStartEdit(doctor)}
                                className="rounded-lg bg-gray-100 px-3.5 py-2 text-sm font-semibold text-gray-700 transition-all duration-150 ease-out hover:bg-gray-200 active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
                              >
                                Edit
                              </button>
                              {tab === 'pending' && (
                                <>
                                  <button
                                    type="button"
                                    onClick={() => handleVerify(doctor)}
                                    disabled={actioningId === doctor.doctorProfileId}
                                    className="rounded-lg bg-indigo-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
                                  >
                                    Verify
                                  </button>
                                  <button
                                    type="button"
                                    onClick={() =>
                                      setRejectItems([{ id: doctor.doctorProfileId, label: doctor.accountName }])
                                    }
                                    className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                                  >
                                    Remove
                                  </button>
                                </>
                              )}
                              {tab === 'verified' && (
                                <button
                                  type="button"
                                  onClick={() => setConfirmingRevokeId(doctor.doctorProfileId)}
                                  className="rounded-lg border border-red-300 bg-white px-3.5 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-500 focus-visible:ring-offset-2"
                                >
                                  Revoke
                                </button>
                              )}
                            </div>
                          ))}
                      </td>
                    </tr>
                  ),
                )}
              </tbody>
            </table>
          </div>
          <PaginationControls
            page={page}
            pageSize={DOCTORS_PAGE_SIZE}
            totalCount={totalCount}
            onPageChange={handlePageChange}
            itemLabel="doctors"
          />
        </>
      )}

      {rejectItems && (
        <RejectConfirmModal
          items={rejectItems}
          entityNoun="doctor"
          onClose={() => setRejectItems(null)}
          onSubmit={handleRejectSubmit}
        />
      )}

      {deleteItems && (
        <DeleteConfirmModal
          items={deleteItems}
          entityNoun="doctor"
          onClose={() => setDeleteItems(null)}
          onSubmit={handleDeleteSubmit}
        />
      )}
    </div>
  )
}
