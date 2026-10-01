import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import { SignupForm } from '../../src/features/patient-account/SignupForm'
import { SignupPatientApiError, signupPatient } from '../../src/features/patient-account/api'

// 071-readable-rate-limit (live-audit finding 5 and the SignupForm fallback gap): every failed
// signup shows a visible reason - a rate limit (with the wait when the server sent one), an
// error code the form does not know, and a request that never reached the server.

function renderForm() {
  render(
    <MemoryRouter>
      <SignupForm />
    </MemoryRouter>,
  )
}

async function submitWith(fetchImpl: typeof fetch) {
  vi.stubGlobal('fetch', vi.fn(fetchImpl))
  const user = userEvent.setup()
  renderForm()
  await user.type(screen.getByLabelText(/^email/i), 'new@example.com')
  await user.type(screen.getByLabelText(/^password/i), 'Str0ng!Pass')
  await user.click(screen.getByRole('button', { name: /create account/i }))
}

function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...headers } })
}

describe('SignupForm error fallbacks', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('explains a rate limit and how long to wait', async () => {
    await submitWith(async () =>
      jsonResponse(
        429,
        { error: 'RATE_LIMIT_EXCEEDED', message: 'Too many requests. Please try again later.' },
        { 'Retry-After': '59' },
      ),
    )

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many signup attempts/i)
    expect(alert).toHaveTextContent(/less than a minute/i)
  })

  it('still explains a rate limit when the wait time is not readable', async () => {
    await submitWith(async () => jsonResponse(429, { error: 'RATE_LIMIT_EXCEEDED' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(/too many signup attempts/i)
    expect(alert).not.toHaveTextContent(/try again in/i)
  })

  it('shows the server message for an error code it does not know', async () => {
    await submitWith(async () => jsonResponse(503, { error: 'SOMETHING_NEW', message: 'Signups are paused.' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Signups are paused.')
  })

  it('shows a generic message for an unknown error without a message', async () => {
    await submitWith(async () => jsonResponse(500, { error: 'SOMETHING_NEW' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/signup failed/i)
  })

  it('says the server could not be reached on a network failure', async () => {
    await submitWith(async () => {
      throw new TypeError('Failed to fetch')
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the server/i)
  })
})

describe('signupPatient', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('reads Retry-After from a throttled response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse(429, { error: 'RATE_LIMIT_EXCEEDED' }, { 'Retry-After': '42' })),
    )

    const error = await signupPatient({ email: 'a@example.com', password: 'x' }).catch((e: unknown) => e)
    expect(error).toBeInstanceOf(SignupPatientApiError)
    expect((error as SignupPatientApiError).retryAfterSeconds).toBe(42)
  })
})
