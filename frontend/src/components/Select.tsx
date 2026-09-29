import { forwardRef, type ReactNode, type SelectHTMLAttributes } from 'react'

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: string
  children: ReactNode
}

// 049-shared-ui-components T013 (research.md Decision 3): thin wrapper around the existing
// `.input` class (index.css) + the label pattern already duplicated at every call site.
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, id, className, children, ...rest },
  ref,
) {
  return (
    <div>
      {label && (
        <label htmlFor={id} className="block text-sm font-medium text-gray-700">
          {label}
        </label>
      )}
      <select ref={ref} id={id} className={`input ${label ? 'mt-1' : ''} ${className ?? ''}`} {...rest}>
        {children}
      </select>
    </div>
  )
})
