import { apiRequest, ApiError } from '../../src/lib/apiClient'

function mockFetchOnce(response: Partial<Response> & { jsonBody?: unknown }) {
  const { jsonBody, ...rest } = response
  vi.spyOn(global, 'fetch').mockResolvedValueOnce({
    ok: rest.ok ?? true,
    status: rest.status ?? 200,
    json: vi.fn().mockResolvedValue(jsonBody),
    ...rest,
  } as unknown as Response)
}

describe('apiClient', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns parsed JSON on a successful response', async () => {
    mockFetchOnce({ ok: true, status: 200, jsonBody: { id: '123' } })
    const result = await apiRequest<{ id: string }>('/api/v1/things/123')
    expect(result).toEqual({ id: '123' })
  })

  it('prefers the backend message over the fallback when present', async () => {
    mockFetchOnce({ ok: false, status: 400, jsonBody: { error: 'SLOT_ALREADY_BOOKED', message: 'This slot is no longer available.' } })
    await expect(
      apiRequest('/api/v1/things', { fallbackMessage: 'Generic fallback message' }),
    ).rejects.toThrow('This slot is no longer available.')
  })

  it('uses the caller-supplied fallback when no backend message is present', async () => {
    mockFetchOnce({ ok: false, status: 400, jsonBody: { error: 'SLOT_ALREADY_BOOKED' } })
    await expect(
      apiRequest('/api/v1/things', { fallbackMessage: 'This slot is no longer available (fallback).' }),
    ).rejects.toThrow('This slot is no longer available (fallback).')
  })

  it('falls back to a generic status-based message when the body is unparseable', async () => {
    vi.spyOn(global, 'fetch').mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: vi.fn().mockRejectedValue(new Error('not json')),
    } as unknown as Response)
    await expect(apiRequest('/api/v1/things')).rejects.toThrow('Request failed (500).')
  })

  it('throws an ApiError carrying the status and parsed body', async () => {
    mockFetchOnce({ ok: false, status: 403, jsonBody: { error: 'FORBIDDEN' } })
    try {
      await apiRequest('/api/v1/things')
      expect.fail('expected apiRequest to throw')
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError)
      expect((err as ApiError).status).toBe(403)
      expect((err as ApiError).body).toEqual({ error: 'FORBIDDEN' })
    }
  })

  it('injects the Authorization header only when a token is supplied', async () => {
    mockFetchOnce({ ok: true, status: 200, jsonBody: {} })
    await apiRequest('/api/v1/things', { token: 'abc123' })
    const [, init] = vi.mocked(fetch).mock.calls[0]
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer abc123')

    mockFetchOnce({ ok: true, status: 200, jsonBody: {} })
    await apiRequest('/api/v1/things')
    const [, init2] = vi.mocked(fetch).mock.calls[1]
    expect((init2?.headers as Record<string, string>).Authorization).toBeUndefined()
  })
})
