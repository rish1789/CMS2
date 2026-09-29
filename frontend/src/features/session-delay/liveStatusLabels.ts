import type { LiveScheduleStatus } from './api'

// 061-doctor-live-status wording, shared with 063-front-desk-walk-in's session picker so both show
// the doctor's schedule status the same way.
export const STATUS_LABELS: Record<LiveScheduleStatus, string> = {
  NOT_STARTED: 'Not started yet',
  ON_TIME: 'On time',
  RUNNING_EARLY: 'Running early',
  DELAYED: 'Delayed',
  COMPLETED: 'Session complete',
}
