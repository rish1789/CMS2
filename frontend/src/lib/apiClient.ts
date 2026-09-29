// Shared HTTP client for every feature's api.ts (045-backend-module-layering's frontend
// counterpart: 046-frontend-api-client). Replaces each feature independently re-declaring
// API_BASE_URL, hand-rolling fetch()+Authorization header, and defining its own *ApiError
// class. See specs/046-frontend-api-client/research.md for the design rationale, in
// particular Decision 2: this is what actually fixes the confirmed bug where
// `defaultMessageFor(body) ?? body.message` made the backend's real error message
// unreachable dead code (defaultMessageFor always returns a string).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

export class ApiError extends Error {
  readonly status: number
  readonly body: unknown

  constructor(status: number, message: string, body: unknown) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

export interface ApiRequestOptions {
  method?: string
  token?: string
  body?: unknown
  /**
   * Feature-supplied fallback used only when the backend response has no usable `message`
   * field of its own (network failure, non-JSON body, or a message-less error body) - the
   * backend's own message always wins when present (research.md Decision 2).
   */
  fallbackMessage?: string | ((body: unknown) => string)
}

interface ErrorBody {
  message?: unknown
}

function isErrorBody(value: unknown): value is ErrorBody {
  return typeof value === 'object' && value !== null
}

function resolveFallback(fallback: ApiRequestOptions['fallbackMessage'], body: unknown, status: number): string {
  if (typeof fallback === 'function') {
    return fallback(body)
  }
  if (typeof fallback === 'string') {
    return fallback
  }
  return `Request failed (${status}).`
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  })

  if (!response.ok) {
    let parsedBody: unknown
    try {
      parsedBody = await response.json()
    } catch {
      parsedBody = undefined
    }
    const backendMessage =
      isErrorBody(parsedBody) && typeof parsedBody.message === 'string' && parsedBody.message.length > 0
        ? parsedBody.message
        : undefined
    const message = backendMessage ?? resolveFallback(options.fallbackMessage, parsedBody, response.status)
    throw new ApiError(response.status, message, parsedBody)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}
