import { useState, type FormEvent } from 'react'
import { loginPatient, LoginPatientApiError, type LoginPatientResponse } from './api'
import { storePatientSession, type StoredPatientSession } from './token'

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
}

export function LoginForm({ onSuccess }: LoginFormProps = {}) {
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<LoginPatientResponse | null>(null)

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

  if (result) {
    return (
      <div className="mx-auto max-w-md rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Logged in</h1>
        <p className="mt-2 text-sm text-gray-600">Welcome back, {result.email}.</p>
      </div>
    )
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Patient login"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Log in</h1>
        <p className="mt-1 text-sm text-gray-600">Use the same account at any clinic.</p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div>
        <label htmlFor="loginEmail" className="block text-sm font-medium text-gray-700">
          Email
        </label>
        <div className="mt-1">
          <input
            id="loginEmail"
            type="email"
            required
            value={form.email}
            onChange={(e) => updateField('email', e.target.value)}
            className="input"
          />
        </div>
      </div>

      <div>
        <label htmlFor="loginPassword" className="block text-sm font-medium text-gray-700">
          Password
        </label>
        <div className="mt-1">
          <input
            id="loginPassword"
            type="password"
            required
            value={form.password}
            onChange={(e) => updateField('password', e.target.value)}
            className="input"
          />
        </div>
      </div>

      <button
        type="submit"
        disabled={submitting}
        className="w-full rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
      >
        {submitting ? 'Logging in…' : 'Log in'}
      </button>
    </form>
  )
}
