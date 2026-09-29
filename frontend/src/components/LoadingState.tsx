import { ListSkeleton } from './ListSkeleton'

export interface LoadingStateProps {
  variant?: 'list' | 'inline'
  rows?: number
}

// 049-shared-ui-components T017 (research.md Decision 4): wraps the existing ListSkeleton for
// list contexts, and formalizes the ad hoc "Loading…" text (ConsultationNoteForm.tsx,
// AppointmentTypeSelect.tsx, ExternalRecordReferenceForm.tsx, DoctorSelect.tsx and others)
// into a simple centered, accessible variant for non-list contexts.
export function LoadingState({ variant = 'inline', rows = 3 }: LoadingStateProps) {
  if (variant === 'list') {
    return <ListSkeleton rows={rows} />
  }
  return (
    <p role="status" className="py-8 text-center text-sm text-gray-500">
      Loading…
    </p>
  )
}
