import { forwardRef, type ButtonHTMLAttributes } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'destructive'

// 049-shared-ui-components T012 (research.md Decision 3): codifies the exact class
// combination already used identically for each variant across the codebase (verified via
// grep across PendingClinicsList/PendingDoctorsList/RejectConfirmModal/ConsultationNoteForm
// and others) - not a new design.
const VARIANT_CLASS: Record<ButtonVariant, string> = {
  primary: 'bg-indigo-600 text-white shadow-sm hover:bg-indigo-500 hover:shadow focus-visible:ring-indigo-500',
  secondary: 'bg-gray-100 text-gray-700 hover:bg-gray-200 focus-visible:ring-gray-400',
  destructive: 'bg-red-600 text-white shadow-sm hover:bg-red-500 hover:shadow focus-visible:ring-red-500',
}

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', className, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      className={`rounded-lg px-3.5 py-2 text-sm font-semibold transition-all duration-150 ease-out active:scale-[0.98] disabled:opacity-50 disabled:pointer-events-none disabled:active:scale-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ${VARIANT_CLASS[variant]} ${className ?? ''}`}
      {...rest}
    />
  )
})
