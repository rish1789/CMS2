import { useEffect, useRef, useState } from 'react'
import {
  claimItem,
  InboxApiError,
  listInboxItems,
  openInboxStream,
  releaseItem,
  resolveItem,
  type InboxItemResponse,
} from './api'
import { InboxItemCard } from './InboxItemCard'
import { loadStaffSession } from '../staff-login/token'

export interface InboxPageProps {
  clinicId: string
}

export function InboxPage({ clinicId }: InboxPageProps) {
  const [items, setItems] = useState<InboxItemResponse[]>([])
  const [error, setError] = useState<string | null>(null)
  const [live, setLive] = useState(true)
  const session = loadStaffSession()
  const token = session?.token
  const streamRef = useRef<{ close: () => void } | null>(null)
  // _diagnostics [MEDIUM] - [INBOX_LIST] - [RACE_CONDITION]: ids the SSE stream has already
  // reported on since mount win over the slower initial GET's (possibly stale-by-then) snapshot
  // for that same id, instead of the GET blindly overwriting the whole array.
  const touchedIds = useRef<Set<string>>(new Set())

  function applyUpdate(updated: InboxItemResponse) {
    setItems((current) => {
      const index = current.findIndex((i) => i.id === updated.id)
      if (updated.status === 'RESOLVED') {
        return current.filter((i) => i.id !== updated.id)
      }
      if (index === -1) {
        return [...current, updated]
      }
      const next = [...current]
      next[index] = updated
      return next
    })
  }

  useEffect(() => {
    if (!token) return
    let cancelled = false
    touchedIds.current = new Set()

    listInboxItems(clinicId, token)
      .then((initial) => {
        if (cancelled) return
        setItems((current) => {
          const currentIds = new Set(current.map((i) => i.id))
          const additions = initial.filter((i) => !touchedIds.current.has(i.id) && !currentIds.has(i.id))
          return additions.length === 0 ? current : [...current, ...additions]
        })
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Failed to load inbox')
      })

    streamRef.current = openInboxStream(
      clinicId,
      token,
      (updated) => {
        touchedIds.current.add(updated.id)
        applyUpdate(updated)
      },
      (status) => {
        if (!cancelled) setLive(status === 'connected')
      },
    )

    return () => {
      cancelled = true
      streamRef.current?.close()
    }
  }, [clinicId, token])

  if (!session) {
    return (
      <div className="mx-auto max-w-2xl rounded-lg border border-gray-200 bg-white p-6 shadow-sm">
        <p className="text-sm text-gray-600">Sign in as staff to view the Inbox.</p>
      </div>
    )
  }

  const act = (action: (clinicId: string, itemId: string, token: string) => Promise<InboxItemResponse>, itemId: string) => {
    action(clinicId, itemId, session.token)
      .then((updated) => {
        touchedIds.current.add(updated.id)
        applyUpdate(updated)
        setError(null)
      })
      .catch((e: unknown) => {
        setError(e instanceof Error ? e.message : 'Action failed')
        // _diagnostics [MEDIUM] - [INBOX_CLAIM] - [STALE_STATE]: a lost claim/release/resolve
        // race leaves local state showing the pre-conflict status - reconcile with a fresh fetch
        // instead of waiting on an eventually-consistent broadcast that may never touch this item
        // again.
        if (e instanceof InboxApiError && (e.body.error === 'ALREADY_CLAIMED' || e.body.error === 'NOT_CLAIMANT')) {
          listInboxItems(clinicId, session.token)
            .then((fresh) => setItems(fresh))
            .catch(() => {
              // Best-effort reconciliation only - the error message above already informed the user.
            })
        }
      })
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="rounded-lg border border-gray-200 bg-white shadow-sm">
        <div className="flex items-center justify-between border-b border-gray-200 p-4">
          <h1 className="text-lg font-semibold text-gray-900">Inbox</h1>
          {!live && (
            <output className="block text-sm text-amber-700">Live updates paused — reconnecting…</output>
          )}
        </div>
        <div className="p-4">
          {error && (
            <p role="alert" className="mb-4 rounded-md bg-red-50 p-3 text-sm text-red-700">
              {error}
            </p>
          )}
          {/* staff-console-audit-2026-09-10 P1: new items stream in over SSE with no signal at
              all for a screen reader user - aria-live announces additions/removals as they
              happen, without needing to re-read the whole list each time (aria-atomic="false"). */}
          <div aria-live="polite" aria-atomic="false">
            {items.length === 0 ? (
              <p className="text-sm text-gray-500">No outstanding items.</p>
            ) : (
              <ul className="space-y-3">
                {items.map((item) => (
                  <InboxItemCard
                    key={item.id}
                    item={item}
                    currentAccountId={session.accountId}
                    onClaim={(id) => act(claimItem, id)}
                    onRelease={(id) => act(releaseItem, id)}
                    onResolve={(id) => act(resolveItem, id)}
                  />
                ))}
              </ul>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}
