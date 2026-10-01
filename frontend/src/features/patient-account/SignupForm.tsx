import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { signupPatient, SignupPatientApiError, type SignupPatientResponse } from './api'
import { FormField } from '../../components/FormField'
import { withReturnTo } from './returnTo'

interface FormState {
  email: string
  password: string
  mobile: string
}

const initialState: FormState = {
  email: '',
  password: '',
  mobile: '',
}

interface FieldErrors {
  email?: string
  password?: string
  passwordRules?: string[]
  mobile?: string
}

export interface SignupFormProps {
  /** 070-login-return-path: carried onto the success screen's "Log in" link. */
  returnTo?: string | null
}

export function SignupForm({ returnTo }: SignupFormProps = {}) {
  const [form, setForm] = useState<FormState>(initialState)
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SignupPatientResponse | null>(null)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  function applyApiError(error: SignupPatientApiError) {
    const body = error.body
    switch (body.error) {
      case 'INVALID_PASSWORD':
        setFieldErrors({ password: body.message, passwordRules: body.failedRules })
        break
      case 'INVALID_MOBILE_NUMBER':
        setFieldErrors({ mobile: body.message })
        break
      case 'EMAIL_ALREADY_IN_USE':
        setFieldErrors({ email: body.message })
        break
      case 'MISSING_REQUIRED_FIELD':
        setFormError(body.message ?? `Missing required field: ${body.field}`)
        break
      case 'SIGNUP_FAILED':
        setFormError(body.message ?? 'Signup failed. Please try again.')
        break
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)
    setFieldErrors({})

    try {
      const response = await signupPatient({
        email: form.email,
        password: form.password,
        mobile: form.mobile || undefined,
      })
      setResult(response)
    } catch (err) {
      if (err instanceof SignupPatientApiError) {
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
        <h1 className="text-lg font-semibold text-gray-900">Account created</h1>
        <p className="mt-2 text-sm text-gray-600">
          You can now log in with {result.email} across any clinic on the platform.
        </p>
        <Link
          to={withReturnTo('/patient/login', returnTo)}
          className="mt-4 inline-block rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-colors duration-150 hover:bg-indigo-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Log in
        </Link>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Patient account signup"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Create your account</h1>
        <p className="mt-1 text-sm text-gray-600">
          One login works across every clinic you visit.
        </p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <FormField label="Email" htmlFor="signupEmail" required error={fieldErrors.email}>
        <input
          id="signupEmail"
          type="email"
          required
          value={form.email}
          onChange={(e) => updateField('email', e.target.value)}
          className="input"
        />
      </FormField>

      <FormField label="Password" htmlFor="signupPassword" required error={fieldErrors.password}>
        <input
          id="signupPassword"
          type="password"
          required
          value={form.password}
          onChange={(e) => updateField('password', e.target.value)}
          className="input"
        />
        {fieldErrors.passwordRules && fieldErrors.passwordRules.length > 0 && (
          <ul className="mt-1 list-inside list-disc text-sm text-red-600">
            {fieldErrors.passwordRules.map((rule) => (
              <li key={rule}>{rule}</li>
            ))}
          </ul>
        )}
      </FormField>

      <FormField
        label="Mobile (optional)"
        htmlFor="signupMobile"
        error={fieldErrors.mobile}
        hint="10-digit Indian mobile number, e.g. 9876543210"
      >
        <input
          id="signupMobile"
          value={form.mobile}
          onChange={(e) => updateField('mobile', e.target.value)}
          className="input"
        />
      </FormField>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Creating account…' : 'Create account'}
      </button>
    </form>
  )
}
