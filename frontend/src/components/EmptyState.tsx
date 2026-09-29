import type { ReactNode } from 'react'

export interface EmptyStateProps {
  message: string
  action?: ReactNode
}

// 049-shared-ui-components T016 (research.md Decision 4): replaces the 6+ ad hoc empty-state
// wrapper duplications (e.g. PatientSearch.tsx's dashed-border "no results" block) with one
// message+optional-action shape.
export function EmptyState({ message, action }: EmptyStateProps) {
  return (
    <div className="rounded-lg border border-dashed border-gray-300 p-6 text-center">
      <p className="text-sm text-gray-500">{message}</p>
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
