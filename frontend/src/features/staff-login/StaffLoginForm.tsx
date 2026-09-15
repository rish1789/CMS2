import { useState, type FormEvent } from 'react'
import { loginStaff, LoginStaffApiError } from './api'
import { storeStaffSession } from './token'
import type { ClinicPortalRole } from './destination'
import { storeSuperAdminSession } from '../super-admin/token'

interface FormState {
  identifier: string
  password: string
}

const initialState: FormState = { identifier: '', password: '' }

export interface StaffLoginFormProps {
  // 040-super-admin-rbac-login: the resolved role is the caller's cue for where to
  // navigate next (see decideClinicPortalDestination) - this form no longer assumes
  // every successful login is staff.
  onSuccess?: (result: { role: ClinicPortalRole }) => void
}

export function StaffLoginForm({ onSuccess }: StaffLoginFormProps) {
  const [form, setForm] = useState<FormState>(initialState)
  const [formError, setFormError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  function updateField<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setFormError(null)

    try {
      const response = await loginStaff({ identifier: form.identifier, password: form.password })
      if (response.role === 'SUPER_ADMIN') {
        storeSuperAdminSession({ token: response.token, username: response.email })
      } else {
        storeStaffSession({
          token: response.token,
          accountId: response.accountId ?? '',
          email: response.email,
        })
      }
      onSuccess?.({ role: response.role })
    } catch (err) {
      if (err instanceof LoginStaffApiError) {
        setFormError(err.body.message ?? 'Invalid email or password.')
      } else {
        setFormError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="mx-auto max-w-md space-y-6 rounded-lg border border-gray-200 bg-white p-6 shadow-sm"
      aria-label="Clinic sign in"
    >
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Clinic sign in</h1>
        <p className="mt-1 text-sm text-gray-600">Sign in with your email or staff code, and your password.</p>
      </div>

      {formError && (
        <p role="alert" className="rounded-md bg-red-50 p-3 text-sm text-red-700">
          {formError}
        </p>
      )}

      <div>
        <label htmlFor="staffLoginIdentifier" className="block text-sm font-medium text-gray-700">
          Email or Staff Code
        </label>
        <div className="mt-1">
          <input
            id="staffLoginIdentifier"
            type="text"
            required
            value={form.identifier}
            onChange={(e) => updateField('identifier', e.target.value)}
            className="input"
          />
        </div>
      </div>

      <div>
        <label htmlFor="staffLoginPassword" className="block text-sm font-medium text-gray-700">
          Password
        </label>
        <div className="mt-1">
          <input
            id="staffLoginPassword"
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
        {submitting ? 'Signing in…' : 'Sign in'}
      </button>
    </form>
  )
}
