import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { loginPatient, LoginPatientApiError, type LoginPatientResponse } from './api'
import { storePatientSession, type StoredPatientSession } from './token'
import { withReturnTo } from './returnTo'
import { useToast } from '../../components/Toast'

interface FormState {
  email: string
  password: string
}

const initialState: FormState = {
  email: '',
  password: '',
}

export interface LoginFormProps {
  onSuccess?: (session: StoredPatientSession) => void
  /** 070-login-return-path: carried onto the "Create an account" link. */
  returnTo?: string | null
}

// 056-design-copy-quality-pass (2026-09-16 login-page rebuild): matches design/
// patient-login-reference.html's card layout/copy structure, reusing this project's own
// tokens (index.css) rather than the reference's own palette/serif font - same substitution
// already applied to HomePage.tsx.
export function LoginForm({ onSuccess, returnTo }: LoginFormProps = {}) {
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<LoginPatientResponse | null>(null)
  const { showToast } = useToast()

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await loginPatient({ email: form.email, password: form.password })
      const session: StoredPatientSession = {
        token: response.token,
        patientAccountId: response.patientAccountId,
        email: response.email,
      }
      storePatientSession(session)
      setResult(response)
      onSuccess?.(session)
    } catch (err) {
      if (err instanceof LoginPatientApiError) {
        setFormError(err.body.message ?? 'Something went wrong. Please try again.')
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  // No password-reset flow exists yet anywhere in this system (frontend or backend) - building
  // one (reset tokens, email delivery) is a real new feature, out of this rebuild's scope
  // (constitution: no live notification delivery integration is an explicit out-of-scope
  // boundary until a documented product decision changes it). This is an honest placeholder,
  // not a dead `#` link pretending the flow exists.
  function handleForgotPassword() {
    showToast("Password reset isn't available yet — contact your clinic for help.")
  }

  if (result) {
    return (
      <div className="w-full max-w-[420px] rounded-2xl border border-gray-200 bg-white p-7 shadow-md">
        <h1 className="text-2xl font-bold text-gray-900">Logged in</h1>
        <p className="mt-2 text-base text-gray-600">Welcome back, {result.email}.</p>
      </div>
    )
  }

  return (
    // 2026-09-16 fit-without-scrolling pass: card padding/heading size and every gap below
    // were tightened (p-9→p-7, text-3xl→text-2xl, space-y-5→space-y-4, etc.) so the whole
    // page (header + card + footer) fits inside a typical laptop viewport without forcing a
    // scroll - PatientLoginPage.tsx/BrandHeader.tsx/BrandFooter.tsx got the matching cut. The
    // page itself is still `min-h-screen` with no `overflow-hidden` anywhere, so the browser's
    // native scrollbar still appears on a genuinely short viewport (e.g. a phone keyboard
    // open) - only the everyday case of "needing to scroll just to see the button" is fixed.
    <div className="w-full max-w-[420px] rounded-2xl border border-gray-200 bg-white p-7 shadow-md">
      <h1 className="text-2xl font-bold text-gray-900">Welcome back.</h1>
      <p className="mt-1.5 mb-6 text-base text-gray-600">Use the same account at every clinic you visit.</p>

      <form onSubmit={handleSubmit} aria-label="Patient login" className="space-y-4">
        {formError && (
          <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
            {formError}
          </p>
        )}

        <div>
          <label htmlFor="loginEmail" className="block text-sm font-semibold text-gray-900">
            Email
          </label>
          <input
            id="loginEmail"
            type="email"
            required
            autoComplete="email"
            value={form.email}
            onChange={(e) => updateField('email', e.target.value)}
            className="input mt-1"
          />
        </div>

        <div>
          <div className="flex items-baseline justify-between">
            <label htmlFor="loginPassword" className="block text-sm font-semibold text-gray-900">
              Password
            </label>
            <button
              type="button"
              onClick={handleForgotPassword}
              className="rounded text-sm font-medium text-indigo-600 transition-colors duration-150 hover:text-indigo-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              Forgot password?
            </button>
          </div>
          <input
            id="loginPassword"
            type="password"
            required
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => updateField('password', e.target.value)}
            className="input mt-1"
          />
        </div>

        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-base font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      <div className="mt-5 flex items-center gap-3 text-sm text-gray-400">
        <span aria-hidden="true" className="h-px flex-1 bg-gray-200" />
        New to CMS2?
        <span aria-hidden="true" className="h-px flex-1 bg-gray-200" />
      </div>

      <p className="mt-3.5 text-center text-sm text-gray-600">
        <Link
          to={withReturnTo('/patient/signup', returnTo)}
          className="rounded font-semibold text-indigo-600 hover:text-indigo-700 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Create an account
        </Link>{' '}
        to get started.
      </p>
    </div>
  )
}
