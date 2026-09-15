import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { onboardStaff, OnboardStaffApiError, type OnboardStaffResponse, type StaffRole } from './api'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'

interface FormState {
  name: string
  email: string
  mobile: string
  role: StaffRole
  specialization: string
  licenseNumber: string
  experienceYears: string
}

const initialState: FormState = {
  name: '',
  email: '',
  mobile: '',
  role: 'Operations',
  specialization: '',
  licenseNumber: '',
  experienceYears: '',
}

export interface OnboardStaffFormProps {
  clinicId: string
}

export function OnboardStaffForm({ clinicId }: OnboardStaffFormProps) {
  const [session, setSession] = useState(() => loadStaffSession())
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<OnboardStaffResponse | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function handleSessionExpired(message: string) {
    storeStaffSession(null)
    setSession(null)
    setFormError(message)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!session) return
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await onboardStaff(
        clinicId,
        {
          name: form.name,
          email: form.email,
          mobile: form.mobile.trim() === '' ? undefined : form.mobile,
          role: form.role,
          doctor:
            form.role === 'Doctor'
              ? {
                  specialization: form.specialization,
                  licenseNumber: form.licenseNumber,
                  experienceYears: Number(form.experienceYears),
                }
              : undefined,
        },
        session.token,
      )
      setResult(response)
    } catch (err) {
      if (err instanceof OnboardStaffApiError) {
        if (err.body.error === 'UNAUTHORIZED') {
          handleSessionExpired(err.message)
        } else {
          setFormError(err.message)
        }
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (!session) {
    return (
      // staff-console-audit-2026-09-10 P2: this used to be a dead end - the message alone, with
      // no way back to sign back in short of the browser's own back button.
      <div className="mx-auto max-w-md space-y-3 rounded-md bg-red-50 p-4">
        <p role="alert" className="text-sm text-red-700">
          {formError ?? 'Please sign in as a ClinicAdmin to onboard staff.'}
        </p>
        <Link
          to="/staff/login"
          className="inline-flex items-center text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700"
        >
          Sign in again
        </Link>
      </div>
    )
  }

  if (result?.existingAccount) {
    return (
      <output className="block mx-auto max-w-md space-y-4 rounded-lg border border-blue-300 bg-blue-50 p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Doctor already has an account</h1>
        <p className="text-sm text-gray-700">
          This doctor is already onboarded at another clinic and has been linked to this clinic
          too — no new password was issued. They should keep using their existing login.
        </p>
        <dl className="space-y-2 rounded-md bg-white p-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Existing staff code</dt>
            <dd className="font-mono text-gray-900">{result.staffCode}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Email</dt>
            <dd className="text-gray-900">{result.email}</dd>
          </div>
        </dl>
        <button
          type="button"
          onClick={() => {
            setResult(null)
            setForm(initialState)
          }}
          className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Onboard another
        </button>
      </output>
    )
  }

  if (result) {
    return (
      <output className="block mx-auto max-w-md space-y-4 rounded-lg border border-amber-300 bg-amber-50 p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Staff onboarded</h1>
        <p className="text-sm text-gray-700">
          Hand these credentials to the new hire directly — they are shown once and cannot be
          retrieved again.
        </p>
        <dl className="space-y-2 rounded-md bg-white p-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Staff code</dt>
            <dd className="font-mono text-gray-900">{result.staffCode}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Temporary password</dt>
            <dd className="font-mono text-gray-900">{result.temporaryPassword}</dd>
          </div>
          <div className="flex justify-between gap-4">
            <dt className="font-medium text-gray-600">Email</dt>
            <dd className="text-gray-900">{result.email}</dd>
          </div>
        </dl>
        <button
          type="button"
          onClick={() => {
            setResult(null)
            setForm(initialState)
          }}
          className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Onboard another
        </button>
      </output>
    )
  }

  return (
    // staff-console-redesign-2026-09-10: a 2-column field grid and a real Cancel action -
    // borrows the layout from a provided design reference (mockup's onboarding.html) rebuilt in
    // this app's own indigo/gray-* design tokens and shared .input class, not a new visual language.
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-xl space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm sm:p-8"
      aria-label="Onboard staff"
    >
      <div className="border-b border-gray-100 pb-5">
        <h1 className="text-lg font-semibold text-gray-900">Onboard staff</h1>
        <p className="mt-1 text-sm text-gray-600">Add a new Doctor or Operations staff member.</p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="onboardName" className="block text-sm font-medium text-gray-700">
            Name
          </label>
          <input
            id="onboardName"
            required
            value={form.name}
            onChange={(e) => updateField('name', e.target.value)}
            className="input mt-1"
          />
        </div>

        <div>
          <label htmlFor="onboardEmail" className="block text-sm font-medium text-gray-700">
            Email
          </label>
          <input
            id="onboardEmail"
            type="email"
            required
            value={form.email}
            onChange={(e) => updateField('email', e.target.value)}
            className="input mt-1"
          />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="onboardMobile" className="block text-sm font-medium text-gray-700">
            Mobile <span className="font-normal text-gray-400">(optional)</span>
          </label>
          <input
            id="onboardMobile"
            value={form.mobile}
            onChange={(e) => updateField('mobile', e.target.value)}
            className="input mt-1"
          />
        </div>

        <div>
          <label htmlFor="onboardRole" className="block text-sm font-medium text-gray-700">
            Role
          </label>
          <select
            id="onboardRole"
            value={form.role}
            onChange={(e) => updateField('role', e.target.value as StaffRole)}
            className="input mt-1"
          >
            <option value="Operations">Operations</option>
            <option value="Doctor">Doctor</option>
          </select>
        </div>
      </div>

      {form.role === 'Doctor' && (
        <div className="space-y-4 rounded-lg border border-gray-200 bg-gray-50 p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-gray-500">Doctor details</p>
          <div className="grid gap-4 sm:grid-cols-2">
            <div>
              <label htmlFor="onboardSpecialization" className="block text-sm font-medium text-gray-700">
                Specialization
              </label>
              <input
                id="onboardSpecialization"
                required
                value={form.specialization}
                onChange={(e) => updateField('specialization', e.target.value)}
                className="input mt-1"
              />
            </div>
            <div>
              <label htmlFor="onboardLicenseNumber" className="block text-sm font-medium text-gray-700">
                License number
              </label>
              <input
                id="onboardLicenseNumber"
                required
                value={form.licenseNumber}
                onChange={(e) => updateField('licenseNumber', e.target.value)}
                className="input mt-1"
              />
            </div>
          </div>
          <div>
            <label htmlFor="onboardExperienceYears" className="block text-sm font-medium text-gray-700">
              Experience <span className="font-normal text-gray-400">(years)</span>
            </label>
            <input
              id="onboardExperienceYears"
              type="number"
              min={0}
              required
              value={form.experienceYears}
              onChange={(e) => updateField('experienceYears', e.target.value)}
              className="input mt-1"
            />
          </div>
        </div>
      )}

      <div className="flex justify-end gap-3 border-t border-gray-100 pt-5">
        <Link
          to={`/staff/clinics/${clinicId}`}
          className="inline-flex h-10 items-center rounded-lg border border-gray-300 px-4 text-sm font-medium text-gray-700 transition-colors duration-150 hover:bg-gray-50"
        >
          Cancel
        </Link>
        <button
          type="submit"
          disabled={submitting}
          className="inline-flex h-10 items-center rounded-lg bg-indigo-600 px-4 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Onboarding…' : 'Onboard staff'}
        </button>
      </div>
    </form>
  )
}
