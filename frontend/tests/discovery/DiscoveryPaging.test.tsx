import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DiscoverySearch } from '../../src/features/discovery/DiscoverySearch'
import {
  listDiscoveryCities,
  listDiscoverySpecializations,
  searchDiscovery,
  type DiscoveryPage,
  type DiscoveryResult,
} from '../../src/features/discovery/api'

vi.mock('../../src/features/discovery/api', async () => {
  const actual = await vi.importActual<typeof import('../../src/features/discovery/api')>(
    '../../src/features/discovery/api',
  )
  return {
    ...actual,
    searchDiscovery: vi.fn(),
    listDiscoveryCities: vi.fn(),
    listDiscoverySpecializations: vi.fn(),
  }
})

const mockedSearch = vi.mocked(searchDiscovery)

// 072-discovery-pagination (live-audit finding 6): every match is reachable, filters and pages
// work together, and an outdated response never replaces a newer one.

function doctor(n: number, name = 'Dr. Same Name'): DiscoveryResult {
  return {
    doctorProfileId: `doctor-${n}`,
    doctorName: name,
    specialization: 'General Medicine',
    experienceYears: 5,
    clinicId: `clinic-${n}`,
    clinicName: `Clinic ${n}`,
    clinicAddress: `${n} Road`,
    clinicCity: 'Pune',
  }
}

// 25 synthetic matches, served 20 per page like the server.
function pageOf(page: number, total = 25): DiscoveryPage {
  const start = page * 20
  const results = Array.from({ length: Math.max(0, Math.min(20, total - start)) }, (_, i) => doctor(start + i + 1))
  return { results, totalCount: total }
}

function renderSearch() {
  return render(<DiscoverySearch />, { wrapper: MemoryRouter })
}

function lastCall() {
  return mockedSearch.mock.calls[mockedSearch.mock.calls.length - 1][0]
}

describe('DiscoverySearch paging', () => {
  beforeEach(() => {
    mockedSearch.mockReset()
    vi.mocked(listDiscoveryCities).mockReset().mockResolvedValue(['Noida', 'Pune'])
    vi.mocked(listDiscoverySpecializations).mockReset().mockResolvedValue(['Cardiology'])
  })

  it('reaches every match with Next and Previous, sending the page with the filters', async () => {
    const user = userEvent.setup()
    mockedSearch.mockImplementation(async (params) => pageOf(params?.page ?? 0))
    renderSearch()

    expect(await screen.findByText('Clinic 1')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 2')).toBeInTheDocument()
    expect(screen.getByText(/1-20 of 25 doctors/)).toBeInTheDocument()
    expect(lastCall()).toEqual(expect.objectContaining({ page: 0, size: 20 }))

    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Clinic 25')).toBeInTheDocument()
    expect(screen.queryByText('Clinic 1')).not.toBeInTheDocument()
    expect(screen.getByText(/21-25 of 25 doctors/)).toBeInTheDocument()
    expect(lastCall()).toEqual(expect.objectContaining({ page: 1, size: 20 }))
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    expect(screen.getByRole('heading', { name: /search results/i })).toHaveFocus()

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByText('Clinic 1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
  })

  it('goes back to the first page when a filter changes, keeping the filter', async () => {
    const user = userEvent.setup()
    mockedSearch.mockImplementation(async (params) => pageOf(params?.page ?? 0))
    renderSearch()
    await screen.findByText('Clinic 1')
    await user.click(screen.getByRole('button', { name: 'Next' }))
    await screen.findByText('Clinic 25')

    await user.selectOptions(screen.getByLabelText(/filter by city/i), 'Noida')

    await waitFor(() => expect(lastCall()).toEqual(expect.objectContaining({ city: 'Noida', page: 0 })))
    expect(await screen.findByText('Page 1 of 2')).toBeInTheDocument()
    // No request ever paired the new filter with the old page.
    expect(mockedSearch.mock.calls.some(([p]) => p?.city === 'Noida' && p?.page === 1)).toBe(false)
  })

  it('ignores a slower, older response that arrives after a newer one', async () => {
    const user = userEvent.setup()
    let resolveOld: (page: DiscoveryPage) => void = () => {}
    mockedSearch
      .mockImplementationOnce(() => new Promise<DiscoveryPage>((resolve) => (resolveOld = resolve)))
      .mockImplementation(async () => ({ results: [doctor(99, 'Dr. Newer Result')], totalCount: 1 }))
    renderSearch()

    await user.type(screen.getByLabelText(/search by doctor or clinic name/i), 'newer')
    expect(await screen.findByText('Dr. Newer Result')).toBeInTheDocument()

    resolveOld({ results: [doctor(1, 'Dr. Older Result')], totalCount: 1 })
    await new Promise((r) => setTimeout(r, 20))
    expect(screen.queryByText('Dr. Older Result')).not.toBeInTheDocument()
    expect(screen.getByText('Dr. Newer Result')).toBeInTheDocument()
  })

  it('retries the same page and filters after a failure', async () => {
    const user = userEvent.setup()
    mockedSearch.mockImplementation(async (params) => pageOf(params?.page ?? 0))
    renderSearch()
    await screen.findByText('Clinic 1')
    mockedSearch.mockRejectedValueOnce(new Error('offline'))
    await user.click(screen.getByRole('button', { name: 'Next' }))

    const alert = await screen.findByRole('alert')
    const failedCall = lastCall()
    await user.click(within(alert).getByRole('button', { name: /try again/i }))

    expect(await screen.findByText('Clinic 25')).toBeInTheDocument()
    expect(lastCall()).toEqual(failedCall)
  })

  it('shows the empty state without paging controls', async () => {
    mockedSearch.mockResolvedValue({ results: [], totalCount: 0 })
    renderSearch()

    expect(await screen.findByText(/no matching clinics or doctors found/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument()
  })
})

describe('searchDiscovery', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('sends page and size and reads the total from X-Total-Count', async () => {
    const actual = await vi.importActual<typeof import('../../src/features/discovery/api')>(
      '../../src/features/discovery/api',
    )
    const fetchMock = vi.fn(
      async () =>
        new Response(JSON.stringify([doctor(1)]), { status: 200, headers: { 'X-Total-Count': '41' } }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const page = await actual.searchDiscovery({ page: 2, size: 20, city: 'Pune' })

    const url = new URL(String((fetchMock.mock.calls[0] as unknown[])[0]))
    expect(url.searchParams.get('page')).toBe('2')
    expect(url.searchParams.get('size')).toBe('20')
    expect(url.searchParams.get('city')).toBe('Pune')
    expect(page).toEqual({ results: [doctor(1)], totalCount: 41 })
  })

  it('never offers a page past what it has seen when the total is unreadable', async () => {
    const actual = await vi.importActual<typeof import('../../src/features/discovery/api')>(
      '../../src/features/discovery/api',
    )
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify([doctor(1), doctor(2)]), { status: 200 })))

    const page = await actual.searchDiscovery({ page: 1, size: 20 })

    expect(page.totalCount).toBe(22)
  })
})
