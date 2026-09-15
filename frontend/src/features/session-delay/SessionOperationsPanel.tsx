import { useState } from 'react'
import { CompleteSlotButton } from './CompleteSlotButton'
import { DelayIndicator } from './DelayIndicator'

// _diagnostics [LOW] - [SESSION_DELAY] - [ORPHANED_COMPONENT]: CompleteSlotButton's onCompleted
// and DelayIndicator's refreshKey were designed for exactly this composition ("recalculation
// happens elsewhere, then the display refreshes") but had never been wired together anywhere in
// the tree.
export interface SessionOperationsPanelProps {
  clinicId: string
  sessionId: string
  slotId: string
}

export function SessionOperationsPanel({ clinicId, sessionId, slotId }: SessionOperationsPanelProps) {
  const [refreshKey, setRefreshKey] = useState(0)

  return (
    <div className="space-y-2">
      <DelayIndicator clinicId={clinicId} sessionId={sessionId} refreshKey={refreshKey} />
      <CompleteSlotButton clinicId={clinicId} slotId={slotId} onCompleted={() => setRefreshKey((k) => k + 1)} />
    </div>
  )
}
