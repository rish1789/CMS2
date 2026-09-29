import type { ReactNode } from 'react'

export interface FormFieldProps {
  label: string
  htmlFor: string
  required?: boolean
  error?: string
  hint?: string
  children: ReactNode
}

// 054-forms-validation-consistency T001: extracted from SignupForm.tsx's local `Field` (the
// fuller of 2 real, already-duplicated implementations - SignupForm's and
// RegistrationForm's, the latter missing only `hint`) rather than bolting an error slot onto
// Input/Select, which no form in this codebase currently uses (research.md Decision 1).
export function FormField({ label, htmlFor, required, error, hint, children }: FormFieldProps) {
  return (
    <div>
      <label htmlFor={htmlFor} className="block text-sm font-medium text-gray-700">
        {label}
        {required && <span aria-hidden="true"> *</span>}
      </label>
      <div className="mt-1">{children}</div>
      {hint && !error && <p className="mt-1 text-sm text-gray-500">{hint}</p>}
      {error && (
        <p role="alert" className="mt-1 text-sm text-red-600">
          {error}
        </p>
      )}
    </div>
  )
}
