import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { deactivateStaff, DeactivateStaffApiError, type DeactivationReason } from '../staff-onboarding/api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'
import { RoleBadge } from '../../components/RoleBadge'
import type { StaffSummary } from './api'

const REASON_OPTIONS: { value: DeactivationReason; label: string }[] = [
  { value: 'RESIGNED', label: 'Resigned' },
  { value: 'SERVICE_NOT_REQUIRED', label: 'Service No Longer Required' },
]

function yearsOfService(joinedAt: string): string {
  const msPerYear = 365.25 * 24 * 60 * 60 * 1000
  const years = (Date.now() - new Date(joinedAt).getTime()) / msPerYear
  return years.toFixed(2)
}

function InfoRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-center justify-between gap-4 py-2">
      <dt className="text-sm text-gray-500">{label}</dt>
      <dd className="text-sm font-medium text-gray-900">{value}</dd>
    </div>
  )
}

interface EmployeeModalProps {
  member: StaffSummary
  clinicId: string
  isSelf: boolean
  onClose: () => void
  onDeactivated: (roleAssignmentId: string) => void
}

// staff-console-audit-2026-09-10 P1: Esc closes the dialog and Tab no longer escapes it into the
// page behind - previously neither worked. A native <dialog> (showModal()) gives us both for
// free, so there's no hand-rolled keydown/focus-trap logic to keep in sync.
export function EmployeeModal({ member, clinicId, isSelf, onClose, onDeactivated }: EmployeeModalProps) {
  const [tab, setTab] = useState<'info' | 'actions'>('info')
  const [reason, setReason] = useState<DeactivationReason | ''>('')
  const [phase, setPhase] = useState<'idle' | 'confirming' | 'submitting'>('idle')
  const [error, setError] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    dialogRef.current?.showModal()
  }, [])

  function handleBackdropClick(event: MouseEvent<HTMLDialogElement>) {
    if (event.target === dialogRef.current) {
      dialogRef.current?.close()
    }
  }

  async function handleConfirmDeactivate() {
    const session = loadStaffSession()
    if (!session || !reason) return

    setPhase('submitting')
    setError(null)
    try {
      await deactivateStaff(clinicId, member.accountId, reason, session.token)
      onDeactivated(member.roleAssignmentId)
      setPhase('idle')
    } catch (err) {
      setPhase('confirming')
      if (err instanceof DeactivateStaffApiError) {
        if (err.body.error === 'UNAUTHORIZED') storeStaffSession(null)
        setError(err.message)
      } else {
        setError('Something went wrong. Please try again.')
      }
    }
  }

  return (
    <dialog
      ref={dialogRef}
      onClose={onClose}
      onClick={handleBackdropClick}
      aria-label={`${member.name} details`}
      className="m-auto flex w-full max-w-lg flex-col overflow-hidden rounded-xl border-0 bg-white p-0 shadow-xl backdrop:bg-gray-900/50 sm:flex-row"
    >
      <div className="flex shrink-0 gap-1 border-b border-gray-200 bg-gray-50 p-2 sm:w-36 sm:flex-col sm:border-b-0 sm:border-r">
        <button
          type="button"
          onClick={() => setTab('info')}
          className={`flex flex-1 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-150 sm:flex-none ${
            tab === 'info' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          <svg aria-hidden="true" viewBox="0 0 20 20" className="h-4 w-4 shrink-0" fill="currentColor">
            <path
              fillRule="evenodd"
              clipRule="evenodd"
              d="M18 10A8 8 0 11 2 10a8 8 0 0116 0zM9 9a1 1 0 011-1h.01a1 1 0 110 2H10a1 1 0 01-1-1zm1 3a1 1 0 00-1 1v3a1 1 0 102 0v-3a1 1 0 00-1-1z"
            />
          </svg>
          Info
        </button>
        <button
          type="button"
          onClick={() => setTab('actions')}
          className={`flex flex-1 items-center gap-2 rounded-lg px-3 py-2 text-sm font-medium transition-colors duration-150 sm:flex-none ${
            tab === 'actions' ? 'bg-indigo-100 text-indigo-700' : 'text-gray-600 hover:bg-gray-100'
          }`}
        >
          <svg aria-hidden="true" viewBox="0 0 20 20" className="h-4 w-4 shrink-0" fill="currentColor">
            <path
              fillRule="evenodd"
              clipRule="evenodd"
              d="M11.49 3.17c-.38-1.56-2.6-1.56-2.98 0a1.532 1.532 0 01-2.286.948c-1.372-.836-2.942.734-2.106 2.106.54.886.061 2.042-.947 2.287-1.561.379-1.561 2.6 0 2.978a1.532 1.532 0 01.947 2.287c-.836 1.372.734 2.942 2.106 2.106a1.532 1.532 0 012.287.947c.379 1.561 2.6 1.561 2.978 0a1.533 1.533 0 012.287-.947c1.372.836 2.942-.734 2.106-2.106a1.533 1.533 0 01.947-2.287c1.561-.379 1.561-2.6 0-2.978a1.532 1.532 0 01-.947-2.287c.836-1.372-.734-2.942-2.106-2.106a1.532 1.532 0 01-2.287-.947zM10 13a3 3 0 100-6 3 3 0 000 6z"
            />
          </svg>
          Actions
        </button>
      </div>

      <div className="max-h-[80vh] flex-1 space-y-4 overflow-y-auto p-5">
        <div className="flex items-start justify-between gap-3">
          <h2 className="text-base font-semibold text-gray-900">
            {member.name}
            {isSelf && <span className="ml-1.5 text-xs font-normal text-gray-400">(You)</span>}
          </h2>
          <button
            type="button"
            onClick={() => dialogRef.current?.close()}
            aria-label="Close"
            className="rounded-md p-1 text-gray-400 transition-colors duration-150 hover:bg-gray-100 hover:text-gray-600"
          >
            <svg aria-hidden="true" viewBox="0 0 20 20" className="h-5 w-5" fill="currentColor">
              <path d="M6.28 5.22a.75.75 0 00-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 101.06 1.06L10 11.06l3.72 3.72a.75.75 0 101.06-1.06L11.06 10l3.72-3.72a.75.75 0 00-1.06-1.06L10 8.94 6.28 5.22z" />
            </svg>
          </button>
        </div>

        {tab === 'info' ? (
          <dl className="divide-y divide-gray-100">
            <InfoRow label="Staff ID" value={member.staffCode} />
            <InfoRow label="Role" value={<RoleBadge role={member.role} />} />
            <InfoRow label="Email" value={member.email} />
            <InfoRow label="Mobile" value={member.mobile ?? '—'} />
            <InfoRow label="Years of Service" value={yearsOfService(member.joinedAt)} />
          </dl>
        ) : (
          <div className="space-y-3">
            {isSelf ? (
              <p className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600">
                You can't deactivate your own account.
              </p>
            ) : !member.active ? (
              <p className="rounded-lg border border-gray-200 bg-gray-50 p-3 text-sm text-gray-600">
                This employee is already inactive.
              </p>
            ) : (
              <>
                {error && (
                  <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
                    {error}
                  </p>
                )}
                {phase !== 'confirming' && (
                  <>
                    <label htmlFor="deactivation-reason" className="block text-sm font-medium text-gray-700">
                      Reason
                    </label>
                    <select
                      id="deactivation-reason"
                      value={reason}
                      onChange={(event) => setReason(event.target.value as DeactivationReason)}
                      className="w-full rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm text-gray-700 focus:border-indigo-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30"
                    >
                      <option value="">Select a reason…</option>
                      {REASON_OPTIONS.map((option) => (
                        <option key={option.value} value={option.value}>
                          {option.label}
                        </option>
                      ))}
                    </select>
                    <button
                      type="button"
                      disabled={!reason}
                      onClick={() => setPhase('confirming')}
                      className="w-full rounded-lg border border-red-300 px-3 py-2 text-sm font-medium text-red-700 transition-colors duration-150 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Deactivate
                    </button>
                  </>
                )}
                {(phase === 'confirming' || phase === 'submitting') && (
                  <div className="space-y-3 rounded-lg border border-amber-200 bg-amber-50 p-3">
                    <p className="text-sm text-amber-900">
                      Are you sure you want to deactivate <strong>{member.name}</strong>? They will immediately
                      lose access to the system.
                    </p>
                    <div className="flex gap-2">
                      <button
                        type="button"
                        onClick={handleConfirmDeactivate}
                        disabled={phase === 'submitting'}
                        className="rounded-lg bg-red-600 px-3.5 py-2 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-red-500 disabled:opacity-50"
                      >
                        {phase === 'submitting' ? 'Deactivating…' : 'Confirm'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setPhase('idle')}
                        disabled={phase === 'submitting'}
                        className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </div>
    </dialog>
  )
}
