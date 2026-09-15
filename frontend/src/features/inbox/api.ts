// Client for the Unified Real-Time Inbox (038).
// See specs/039-unified-realtime-inbox/contracts/inbox.md

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export type InboxItemType = 'WALK_IN' | 'WAITLIST_OFFER' | 'DEVERIFICATION_CASCADE'
export type InboxItemStatus = 'UNCLAIMED' | 'CLAIMED' | 'RESOLVED'

export interface InboxItemResponse {
  id: string
  itemType: InboxItemType
  status: InboxItemStatus
  claimedByAccountId: string | null
  claimedByName: string | null
  createdAt: string
  summary: Record<string, unknown>
}

export type InboxErrorBody =
  | { error: 'FORBIDDEN'; message?: string }
  | { error: 'INBOX_ITEM_NOT_FOUND'; message?: string }
  | { error: 'ALREADY_CLAIMED'; message?: string }
  | { error: 'NOT_CLAIMANT'; message?: string }
  | { error: 'UNAUTHORIZED'; message?: string }

export class InboxApiError extends Error {
  readonly body: InboxErrorBody

  constructor(body: InboxErrorBody) {
    super(defaultMessageFor(body) ?? body.message)
    this.name = 'InboxApiError'
    this.body = body
  }
}

function defaultMessageFor(body: InboxErrorBody): string {
  switch (body.error) {
    case 'FORBIDDEN':
      return 'You do not have access to this clinic.'
    case 'INBOX_ITEM_NOT_FOUND':
      return 'This item could not be found.'
    case 'ALREADY_CLAIMED':
      return 'Someone else already claimed this item.'
    case 'NOT_CLAIMANT':
      return 'Only the person who claimed this item can do that.'
    case 'UNAUTHORIZED':
      return 'Your session has expired. Please sign in again.'
    default:
      return 'Something went wrong. Please try again.'
  }
}

async function handle(response: Response): Promise<InboxItemResponse> {
  if (!response.ok) {
    let body: InboxErrorBody
    try {
      body = (await response.json()) as InboxErrorBody
    } catch {
      body = { error: 'INBOX_ITEM_NOT_FOUND' }
    }
    throw new InboxApiError(body)
  }
  return (await response.json()) as InboxItemResponse
}

export async function listInboxItems(clinicId: string, token: string): Promise<InboxItemResponse[]> {
  const response = await fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/inbox`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    let body: InboxErrorBody
    try {
      body = (await response.json()) as InboxErrorBody
    } catch {
      body = { error: 'FORBIDDEN' }
    }
    throw new InboxApiError(body)
  }
  return (await response.json()) as InboxItemResponse[]
}

function post(clinicId: string, itemId: string, action: 'claim' | 'release' | 'resolve', token: string) {
  return fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/inbox/${itemId}/${action}`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token}` },
  }).then(handle)
}

export const claimItem = (clinicId: string, itemId: string, token: string) => post(clinicId, itemId, 'claim', token)
export const releaseItem = (clinicId: string, itemId: string, token: string) => post(clinicId, itemId, 'release', token)
export const resolveItem = (clinicId: string, itemId: string, token: string) => post(clinicId, itemId, 'resolve', token)

/**
 * research.md R2: consumes the SSE stream via `fetch`'s streaming response body, not the native
 * `EventSource` API - EventSource can't set the Authorization header this endpoint requires, and
 * a query-param-token fallback would leak the staff JWT into server access logs.
 */
export function openInboxStream(
  clinicId: string,
  token: string,
  onItem: (item: InboxItemResponse) => void,
  onStatusChange?: (status: 'connected' | 'disconnected') => void,
): { close: () => void } {
  const controller = new AbortController()
  let closed = false

  function connect() {
    fetch(`${API_BASE_URL}/api/v1/clinics/${clinicId}/inbox/stream`, {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    })
      .then(async (response) => {
        // _diagnostics [MEDIUM] - [INBOX_SSE] - [MISSING_ERROR_HANDLING]: a non-2xx JSON error
        // body (e.g. 403 FORBIDDEN) must not be parsed as an SSE byte stream.
        if (!response.ok) return
        onStatusChange?.('connected')
        const reader = response.body?.getReader()
        if (!reader) return
        const decoder = new TextDecoder()
        let buffer = ''
        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          const frames = buffer.split('\n\n')
          buffer = frames.pop() ?? ''
          for (const frame of frames) {
            const dataLine = frame.split('\n').find((line) => line.startsWith('data:'))
            if (!dataLine) continue
            try {
              onItem(JSON.parse(dataLine.slice('data:'.length).trim()) as InboxItemResponse)
            } catch {
              // Malformed frame - skip it, the connection stays open for the next one.
            }
          }
        }
      })
      .catch(() => {
        // Aborted (close() called) or the connection dropped.
      })
      .finally(() => {
        if (closed) return
        // _diagnostics [MEDIUM] - [INBOX_SSE] - [SILENT_FAILURE]: the connection ends on the
        // server's 30-minute SseEmitter timeout, or drops for any other reason - reconnect
        // rather than leaving the page silently stale. listInboxItems() re-syncing full state on
        // every reconnect makes an immediate resubscribe cheap and safe.
        onStatusChange?.('disconnected')
        setTimeout(connect, 1000)
      })
  }

  connect()

  return {
    close: () => {
      closed = true
      controller.abort()
    },
  }
}
