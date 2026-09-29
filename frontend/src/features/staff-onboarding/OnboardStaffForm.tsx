import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { onboardStaff, type OnboardStaffErrorBody, type OnboardStaffResponse, type StaffRole } from './api'
import { ApiError } from '../../lib/apiClient'
import { loadStaffSession, storeStaffSession } from '../staff-login/token'
import { FormField } from '../../components/FormField'

interface FormState {
  name: string
  email: string
  mobile: string
  role: StaffRole
  specialization: string
  licenseNumber: string
  experienceYears: string
}

interface FieldErrors {
  name?: string
  email?: string
  role?: string
  mobile?: string
}

// Same pattern backend/src/main/java/com/cms/common/IndianMobileNumberValidator.java enforces
// server-side (research.md Decision 3) - not a re-derived approximation.
const INDIAN_MOBILE_PATTERN = /^(?:\+91|0)?[6-9]\d{9}$/

// Only these backend `field` values (OnboardStaffRequest's own top-level record components)
// have a matching FormField error slot. `doctor`/`doctor.specialization`/etc. do not (research.md
// Decision 4, FR-006) - those fall back to the top-level banner rather than an invented mapping.
function fieldErrorKeyFor(field: string): keyof FieldErrors | null {
  return field === 'name' || field === 'email' || field === 'role' || field === 'mobile' ? field : null
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
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
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
    setFormError(null)

    const errors: FieldErrors = {}
    if (form.name.trim() === '') {
      errors.name = 'Name is required.'
    }
    if (form.email.trim() === '') {
      errors.email = 'Email is required.'
    }
    if (form.role.trim() === '') {
      errors.role = 'Role is required.'
    }
    if (form.mobile.trim() !== '' && !INDIAN_MOBILE_PATTERN.test(form.mobile.trim())) {
      errors.mobile = 'Mobile number must be a valid Indian number.'
    }
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      return
    }

    setSubmitting(true)

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
      if (err instanceof ApiError) {
        const body = err.body as OnboardStaffErrorBody | undefined
        const fieldKey =
          body?.error === 'MISSING_REQUIRED_FIELD' || body?.error === 'INVALID_MOBILE_NUMBER'
            ? fieldErrorKeyFor(body.field)
            : null
        if (body?.error === 'UNAUTHORIZED') {
          handleSessionExpired(err.message)
        } else if (fieldKey) {
          setFieldErrors({ [fieldKey]: err.message })
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
    // 056-design-copy-quality-pass: dropped the mx-auto max-w-xl this form used to carry -
    // ClinicShell's content pane already caps width (research.md Decision 2), so this was a
    // third, redundant centered box nested inside two others - the actual root cause of the
    // "wasted space" complaint this screen was originally named for.
    <form
      onSubmit={handleSubmit}
      className="space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm sm:p-8"
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
        {/* No `required` HTML attribute on the inputs below: the pre-submit fieldErrors checks
            in handleSubmit are what actually surface (native `required` would otherwise block
            the submit event itself, showing a browser tooltip instead of this form's own
            inline error - the same fix applied to staff BookSlotForm/WalkInForm). */}
        <FormField label="Name" htmlFor="onboardName" required error={fieldErrors.name}>
          <input
            id="onboardName"
            value={form.name}
            onChange={(e) => updateField('name', e.target.value)}
            className="input"
          />
        </FormField>

        <FormField label="Email" htmlFor="onboardEmail" required error={fieldErrors.email}>
          <input
            id="onboardEmail"
            type="email"
            value={form.email}
            onChange={(e) => updateField('email', e.target.value)}
            className="input"
          />
        </FormField>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <FormField
          label="Mobile"
          htmlFor="onboardMobile"
          hint="10-digit Indian mobile number, e.g. 9876543210"
          error={fieldErrors.mobile}
        >
          <input
            id="onboardMobile"
            value={form.mobile}
            onChange={(e) => updateField('mobile', e.target.value)}
            className="input"
          />
        </FormField>

        <FormField label="Role" htmlFor="onboardRole" required error={fieldErrors.role}>
          <select
            id="onboardRole"
            value={form.role}
            onChange={(e) => updateField('role', e.target.value as StaffRole)}
            className="input"
          >
            <option value="Operations">Operations</option>
            <option value="Doctor">Doctor</option>
          </select>
        </FormField>
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
