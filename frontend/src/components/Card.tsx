import type { HTMLAttributes } from 'react'

// 049-shared-ui-components T014 (research.md Decision 4): the exact tile treatment confirmed
// duplicated across AdminDashboard/PatientDashboard/ClinicToolsDashboard/PendingClinicsList/
// ScheduleForm/BookSlotForm. Plain content wrapper - no link/click behavior baked in, since
// those 6 call sites use it differently (some wrap it in a Link, some in a button).
export function Card({ className, ...rest }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={`rounded-xl border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:shadow-md active:scale-[0.98] ${className ?? ''}`}
      {...rest}
    />
  )
}
