import { useState } from 'react'
import { AppearedButton } from './AppearedButton'
import { CompleteSlotButton } from './CompleteSlotButton'
import { LiveScheduleStatusIndicator } from './LiveScheduleStatusIndicator'

// _diagnostics [LOW] - [SESSION_DELAY] - [ORPHANED_COMPONENT]: CompleteSlotButton's onCompleted
// and DelayIndicator's refreshKey were designed for exactly this composition ("recalculation
// happens elsewhere, then the display refreshes") but had never been wired together anywhere in
// the tree.
//
// 061-doctor-live-status: DelayIndicator (the old "On time"/"Running N min behind" text, cached
// and only trigger-recalculated) is superseded here by LiveScheduleStatusIndicator, which is a
// strict enhancement - it covers everything DelayIndicator showed (on time/delayed) plus what it
// structurally couldn't (running early, not started, session complete), computed fresh on every
// poll (spec Assumption A6). DelayIndicator.tsx itself is left in place, unused here, since
// backlog 023's own trigger-based Session.delayMinutes mechanism may still have other consumers.
//
// 057-day-sheet-status-overhaul: both buttons render unconditionally for non-doctor callers,
// mirroring this panel's existing "just try it, the backend enforces real eligibility" design
// (it already had no way to know the slot's current status before this feature either) - a
// BOOKED/NO_SHOW slot's caller uses Appeared, an APPEARED slot's caller uses Completed; the
// other button simply fails with a real error if clicked on the wrong status. AppearedButton is
// never rendered for a doctor caller (FR-007) - this page is reachable directly from a doctor's
// own "Mark completed" link on an Appeared slot, so hiding Appeared here (not just in
// SessionSlotsView's table) is required, not optional.
export interface SessionOperationsPanelProps {
  clinicId: string
  sessionId: string
  slotId: string
  isDoctor: boolean
}

export function SessionOperationsPanel({ clinicId, sessionId, slotId, isDoctor }: SessionOperationsPanelProps) {
  const [refreshKey, setRefreshKey] = useState(0)

  return (
    <div className="space-y-2">
      <LiveScheduleStatusIndicator mode="staff" clinicId={clinicId} sessionId={sessionId} refreshKey={refreshKey} />
      {!isDoctor && (
        <AppearedButton clinicId={clinicId} slotId={slotId} onAppeared={() => setRefreshKey((k) => k + 1)} />
      )}
      <CompleteSlotButton clinicId={clinicId} slotId={slotId} onCompleted={() => setRefreshKey((k) => k + 1)} />
    </div>
  )
}
