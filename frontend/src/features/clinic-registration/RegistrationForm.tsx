import { useState, type FormEvent } from 'react'
import {
  registerClinic,
  RegisterClinicApiError,
  type RegisterClinicResponse,
} from './api'

interface FormState {
  clinicName: string
  clinicAddress: string
  clinicCity: string
  clinicContactEmail: string
  clinicContactMobile: string
  adminName: string
  adminEmail: string
  adminPassword: string
  adminMobile: string
}

const initialState: FormState = {
  clinicName: '',
  clinicAddress: '',
  clinicCity: '',
  clinicContactEmail: '',
  clinicContactMobile: '',
  adminName: '',
  adminEmail: '',
  adminPassword: '',
  adminMobile: '',
}

interface FieldErrors {
  clinicContactMobile?: string
  adminEmail?: string
  adminPassword?: string
  adminPasswordRules?: string[]
  adminMobile?: string
}

export function RegistrationForm() {
  const [form, setForm] = useState<FormState>(initialState)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<RegisterClinicResponse | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function applyApiError(error: RegisterClinicApiError) {
    const body = error.body
    switch (body.error) {
      case 'INVALID_PASSWORD':
        setFieldErrors({ adminPassword: body.message, adminPasswordRules: body.failedRules })
        break
      case 'INVALID_MOBILE_NUMBER':
        setFieldErrors(
          body.field === 'admin.mobile'
            ? { adminMobile: body.message }
            : { clinicContactMobile: body.message },
        )
        break
      case 'EMAIL_ALREADY_IN_USE':
        setFieldErrors({ adminEmail: body.message })
        break
      case 'MISSING_REQUIRED_FIELD':
        setFormError(body.message ?? `Missing required field: ${body.field}`)
        break
      case 'REGISTRATION_FAILED':
        setFormError(body.message ?? 'Registration failed. Please try again.')
        break
      default:
        // _diagnostics [HIGH] - [CLINIC_REGISTRATION] - [SILENT_FAILURE]: no branch here at all
        // previously meant an unrecognized/malformed error code produced no visible feedback -
        // the submit button simply re-enabled with nothing shown.
        setFormError('Something went wrong. Please try again.')
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    setFieldErrors({})

    try {
      const response = await registerClinic({
        clinic: {
          name: form.clinicName,
          address: form.clinicAddress,
          city: form.clinicCity || undefined,
          contactEmail: form.clinicContactEmail || undefined,
          contactMobile: form.clinicContactMobile || undefined,
        },
        admin: {
          name: form.adminName,
          email: form.adminEmail,
          password: form.adminPassword,
          mobile: form.adminMobile || undefined,
        },
      })
      setResult(response)
    } catch (err) {
      if (err instanceof RegisterClinicApiError) {
        applyApiError(err)
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Clinic registered</h1>
        <p className="mt-2 text-sm text-gray-600">
          {result.clinicName} has been registered and is awaiting verification before it appears
          in public search.
        </p>
        <dl className="mt-4 space-y-1 text-sm">
          <div className="flex justify-between">
            <dt className="text-gray-500">Admin email</dt>
            <dd className="font-medium text-gray-900">{result.admin.email}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-gray-500">Staff code</dt>
            <dd className="font-medium text-gray-900">{result.admin.staffCode}</dd>
          </div>
        </dl>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Clinic registration"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Register your clinic</h1>
        <p className="mt-1 text-sm text-gray-600">
          Create your clinic and your own admin account in one step.
        </p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <fieldset className="space-y-4">
        <legend className="text-sm font-medium text-gray-900">Clinic details</legend>

        <Field label="Clinic name" htmlFor="clinicName" required>
          <input
            id="clinicName"
            required
            value={form.clinicName}
            onChange={(e) => updateField('clinicName', e.target.value)}
            className="input"
          />
        </Field>

        <Field label="Address" htmlFor="clinicAddress" required>
          <input
            id="clinicAddress"
            required
            value={form.clinicAddress}
            onChange={(e) => updateField('clinicAddress', e.target.value)}
            className="input"
          />
        </Field>

        <Field label="City (optional)" htmlFor="clinicCity">
          <input
            id="clinicCity"
            value={form.clinicCity}
            onChange={(e) => updateField('clinicCity', e.target.value)}
            className="input"
          />
        </Field>

        <Field label="Contact email (optional)" htmlFor="clinicContactEmail">
          <input
            id="clinicContactEmail"
            type="email"
            value={form.clinicContactEmail}
            onChange={(e) => updateField('clinicContactEmail', e.target.value)}
            className="input"
          />
        </Field>

        <Field
          label="Contact mobile (optional)"
          htmlFor="clinicContactMobile"
          error={fieldErrors.clinicContactMobile}
        >
          <input
            id="clinicContactMobile"
            value={form.clinicContactMobile}
            onChange={(e) => updateField('clinicContactMobile', e.target.value)}
            className="input"
          />
        </Field>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="text-sm font-medium text-gray-900">Your admin account</legend>

        <Field label="Your name" htmlFor="adminName" required>
          <input
            id="adminName"
            required
            value={form.adminName}
            onChange={(e) => updateField('adminName', e.target.value)}
            className="input"
          />
        </Field>

        <Field label="Email" htmlFor="adminEmail" required error={fieldErrors.adminEmail}>
          <input
            id="adminEmail"
            type="email"
            required
            value={form.adminEmail}
            onChange={(e) => updateField('adminEmail', e.target.value)}
            className="input"
          />
        </Field>

        <Field label="Password" htmlFor="adminPassword" required error={fieldErrors.adminPassword}>
          <input
            id="adminPassword"
            type="password"
            required
            value={form.adminPassword}
            onChange={(e) => updateField('adminPassword', e.target.value)}
            className="input"
          />
          {fieldErrors.adminPasswordRules && fieldErrors.adminPasswordRules.length > 0 && (
            <ul className="mt-1 list-inside list-disc text-sm text-red-600">
              {fieldErrors.adminPasswordRules.map((rule) => (
                <li key={rule}>{rule}</li>
              ))}
            </ul>
          )}
        </Field>

        <Field label="Mobile (optional)" htmlFor="adminMobile" error={fieldErrors.adminMobile}>
          <input
            id="adminMobile"
            value={form.adminMobile}
            onChange={(e) => updateField('adminMobile', e.target.value)}
            className="input"
          />
        </Field>
      </fieldset>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Registering…' : 'Register clinic'}
      </button>
    </form>
  )
}

interface FieldProps {
  label: string
  htmlFor: string
  required?: boolean
  error?: string
  children: React.ReactNode
}

function Field({ label, htmlFor, required, error, children }: FieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <div className="mt-1">{children}</div>
      {error && (
        <p role="alert" className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
