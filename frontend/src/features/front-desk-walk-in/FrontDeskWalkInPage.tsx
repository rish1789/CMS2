import { useState, type FormEvent, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError } from '../../lib/apiClient'
import { loadStaffSession } from '../staff-login/token'
import { AppointmentTypeSelect } from '../appointment-types/AppointmentTypeSelect'
import type { SessionSummary } from '../day-sheet/api'
import { registerWalkIn, type RegisterWalkInRequest, type WalkInRegistration } from './api'
import { PatientStep, type PatientChoice } from './PatientStep'
import { SessionStep } from './SessionStep'
import { VisitReasonStep } from './VisitReasonStep'
import { WalkInLinePanel } from './WalkInLinePanel'
import { visitReasonLabel, type VisitReason } from './visitReasons'
import { DuplicatePhoneConflict } from '../patient-search/DuplicatePhoneConflict'
import { duplicatePhoneConflictOf, type DuplicatePhoneConflictBody } from '../patient-search/duplicatePhoneConflict'

interface FieldErrors {
  patient?: string
  reason?: string
  reasonDetail?: string
  session?: string
  appointmentType?: string
}

function Section({ step, title, children }: { step: number; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={`walk-in-step-${step}`} className="space-y-3">
      <h2 id={`walk-in-step-${step}`} className="flex items-center gap-2 text-sm font-semibold text-gray-900">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-indigo-50 text-xs font-semibold text-indigo-700">
          {step}
        </span>
        {title}
      </h2>
      {children}
    </section>
  )
}

function RegistrationResult({ result, onNext }: { result: WalkInRegistration; onNext: () => void }) {
  const isQueue = result.mode === 'QUEUE'
  return (
    <div className="space-y-4 rounded-xl border border-gray-200 bg-white p-6 shadow-sm">
      <p className="text-sm font-medium text-green-700">Walk-in registered</p>
      <div className="flex items-baseline gap-3">
        <span className="text-3xl font-semibold tabular-nums text-gray-900">
          {isQueue ? `Token ${result.tokenNumber}` : `W${result.tokenNumber}`}
        </span>
        <span className="text-sm text-gray-600">{result.patientName}</span>
      </div>
      <p className="text-sm text-gray-700">
        {isQueue
          ? `In ${result.doctorName}’s token queue.`
          : `Position ${result.walkInPosition} in ${result.doctorName}’s walk-in line.`}
      </p>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
        <dt className="text-gray-500">Reason</dt>
        <dd className="text-gray-900">{visitReasonLabel(result.visitReason, result.visitReasonDetail)}</dd>
        <dt className="text-gray-500">Fee</dt>
        <dd className="tabular-nums text-gray-900">₹{result.lockedFee.toFixed(2)}</dd>
      </dl>
      <button
        type="button"
        onClick={onNext}
        className="rounded-lg bg-indigo-600 px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        Register another walk-in
      </button>
    </div>
  )
}

const EMPTY_PATIENT: PatientChoice = { mode: 'existing', patientId: '', name: null }

export function FrontDeskWalkInPage({ clinicId }: { clinicId: string }) {
  const [searchParams] = useSearchParams()
  const [session] = useState(() => loadStaffSession())
  const [patient, setPatient] = useState<PatientChoice>(EMPTY_PATIENT)
  const [reason, setReason] = useState<VisitReason | ''>('')
  const [reasonDetail, setReasonDetail] = useState('')
  const [selectedSession, setSelectedSession] = useState<SessionSummary | null>(null)
  const [preselectedSessionId] = useState(() => searchParams.get('sessionId'))
  const [appointmentTypeId, setAppointmentTypeId] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [duplicatePrompt, setDuplicatePrompt] = useState<string | null>(null)
  const [phoneConflict, setPhoneConflict] = useState<DuplicatePhoneConflictBody | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<WalkInRegistration | null>(null)
  const [refreshKey, setRefreshKey] = useState(0)

  if (!session) {
    return <p className="text-sm text-gray-600">Please sign in as staff to register walk-ins.</p>
  }
  const token = session.token
  const sessionId = selectedSession?.sessionId ?? preselectedSessionId ?? ''

  function validate(): FieldErrors {
    const errors: FieldErrors = {}
    if (patient.mode === 'existing' && patient.patientId === '') errors.patient = 'Select a patient.'
    if (patient.mode === 'new' && patient.name.trim() === '') errors.patient = "Enter the new patient's name."
    if (reason === '') errors.reason = 'Choose why the patient came in.'
    if (reason === 'OTHER' && reasonDetail.trim() === '') errors.reasonDetail = 'Describe the reason for the visit.'
    if (!selectedSession) errors.session = 'Choose a doctor.'
    else if (appointmentTypeId === '') errors.appointmentType = 'Choose an appointment type.'
    return errors
  }

  async function submit(confirmDuplicate: boolean) {
    if (!selectedSession || reason === '') return
    const payload: RegisterWalkInRequest = {
      sessionId: selectedSession.sessionId,
      appointmentTypeId,
      visitReason: reason,
      visitReasonDetail: reason === 'OTHER' ? reasonDetail.trim() : undefined,
      confirmDuplicate,
      ...(patient.mode === 'existing'
        ? { patientId: patient.patientId }
        : {
            patientName: patient.name.trim(),
            patientPhone: patient.phone.trim() || undefined,
            patientEmail: patient.email.trim() || undefined,
          }),
    }
    setSubmitting(true)
    setFormError(null)
    setDuplicatePrompt(null)
    setPhoneConflict(null)
    try {
      setResult(await registerWalkIn(clinicId, payload, token))
      setRefreshKey((k) => k + 1)
    } catch (err) {
      const code = err instanceof ApiError ? (err.body as { error?: string } | undefined)?.error : undefined
      // 074-duplicate-patient-phone: everything entered stays; staff can switch to the existing patient.
      const conflict = err instanceof ApiError ? duplicatePhoneConflictOf(err.body) : null
      if (conflict) {
        setPhoneConflict(conflict)
      } else if (code === 'DUPLICATE_WALK_IN') {
        setDuplicatePrompt((err as ApiError).message)
      } else {
        setFormError(err instanceof Error ? err.message : 'Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validate()
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) return
    void submit(false)
  }

  function startOver() {
    setResult(null)
    setPatient(EMPTY_PATIENT)
    setReason('')
    setReasonDetail('')
    setAppointmentTypeId('')
    setFieldErrors({})
  }

  return (
    <div className="mx-auto max-w-6xl space-y-4">
      <header>
        <h1 className="text-xl font-semibold text-gray-900">Walk-in</h1>
        <p className="text-sm text-gray-600">Register a patient who has arrived without an appointment.</p>
      </header>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_22rem]">
        {result ? (
          <RegistrationResult result={result} onNext={startOver} />
        ) : (
          <form
            onSubmit={handleSubmit}
            noValidate
            aria-label="Register walk-in"
            className="space-y-6 rounded-xl border border-gray-200 bg-white p-6 shadow-sm"
          >
            {formError && (
              <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
                {formError}
              </p>
            )}

            <Section step={1} title="Patient">
              {phoneConflict && (
                <DuplicatePhoneConflict
                  conflict={phoneConflict}
                  actionLabel={(name) => `Use ${name}`}
                  onUseExisting={(existing) => {
                    setPatient({ mode: 'existing', patientId: existing.id, name: existing.name })
                    setPhoneConflict(null)
                  }}
                />
              )}
              <PatientStep clinicId={clinicId} token={token} value={patient} onChange={setPatient} error={fieldErrors.patient} />
            </Section>

            <Section step={2} title="Reason">
              <VisitReasonStep
                reason={reason}
                detail={reasonDetail}
                onChange={(r, d) => {
                  setReason(r)
                  setReasonDetail(d)
                }}
                reasonError={fieldErrors.reason}
                detailError={fieldErrors.reasonDetail}
              />
            </Section>

            <Section step={3} title="Doctor">
              <SessionStep
                clinicId={clinicId}
                token={token}
                value={sessionId}
                refreshKey={refreshKey}
                onChange={(s) => {
                  setSelectedSession(s)
                  setAppointmentTypeId('')
                }}
                onLoaded={(sessions) => {
                  if (!selectedSession && preselectedSessionId) {
                    const match = sessions.find((s) => s.sessionId === preselectedSessionId)
                    if (match) setSelectedSession(match)
                  }
                }}
              />
              {fieldErrors.session && (
                <p role="alert" className="text-sm text-red-600">
                  {fieldErrors.session}
                </p>
              )}
              {selectedSession && (
                <div>
                  <label htmlFor="walkInAppointmentType" className="block text-sm font-medium text-gray-700">
                    Appointment type
                  </label>
                  <AppointmentTypeSelect
                    id="walkInAppointmentType"
                    doctorProfileId={selectedSession.doctorProfileId}
                    token={token}
                    value={appointmentTypeId}
                    onChange={setAppointmentTypeId}
                  />
                  {fieldErrors.appointmentType && (
                    <p role="alert" className="mt-1 text-sm text-red-600">
                      {fieldErrors.appointmentType}
                    </p>
                  )}
                </div>
              )}
            </Section>

            {duplicatePrompt && (
              <div role="alertdialog" aria-label="Patient already in this session" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm">
                <p className="text-amber-900">{duplicatePrompt}</p>
                <div className="mt-2 flex gap-2">
                  <button
                    type="button"
                    disabled={submitting}
                    onClick={() => void submit(true)}
                    className="rounded-md bg-amber-600 px-3 py-1.5 text-sm font-semibold text-white hover:bg-amber-500 disabled:opacity-50"
                  >
                    Register again
                  </button>
                  <button
                    type="button"
                    onClick={() => setDuplicatePrompt(null)}
                    className="rounded-md border border-gray-300 bg-white px-3 py-1.5 text-sm font-medium text-gray-700 hover:bg-gray-50"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={submitting}
              className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              {submitting ? 'Registering…' : 'Register walk-in'}
            </button>
          </form>
        )}

        <aside aria-label="Waiting line">
          {selectedSession ? (
            <WalkInLinePanel
              clinicId={clinicId}
              token={token}
              sessionId={selectedSession.sessionId}
              mode={selectedSession.mode}
              doctorName={selectedSession.doctorName}
              refreshKey={refreshKey}
              onChanged={() => setRefreshKey((k) => k + 1)}
            />
          ) : (
            <div className="rounded-xl border border-dashed border-gray-300 p-6 text-sm text-gray-600">
              Choose a doctor to see who’s waiting.
            </div>
          )}
        </aside>
      </div>
    </div>
  )
}
