import { forwardRef, type InputHTMLAttributes } from 'react'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: string
}

// 049-shared-ui-components T013 (research.md Decision 3): thin wrapper around the existing
// `.input` class (index.css) + the label pattern already duplicated at every call site.
export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, id, className, ...rest },
  ref,
) {
  return (
    <div>
      {label && (
        <label htmlFor={id} className="block text-sm font-medium text-gray-700">
          {label}
        </label>
      )}
      <input ref={ref} id={id} className={`input ${label ? 'mt-1' : ''} ${className ?? ''}`} {...rest} />
    </div>
  )
})
