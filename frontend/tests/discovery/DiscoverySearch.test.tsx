import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DiscoverySearch } from '../../src/features/discovery/DiscoverySearch'
import { listDiscoveryCities, listDiscoverySpecializations, searchDiscovery } from '../../src/features/discovery/api'

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

const mockedSearchDiscovery = vi.mocked(searchDiscovery)
const mockedListCities = vi.mocked(listDiscoveryCities)
const mockedListSpecializations = vi.mocked(listDiscoverySpecializations)

const ELIGIBLE_RESULT = {
  doctorProfileId: 'doctor-1',
  doctorName: 'Dr. Asha Rao',
  specialization: 'Cardiology',
  experienceYears: 8,
  clinicId: 'clinic-1',
  clinicName: 'Sunrise Clinic',
  clinicAddress: '42 Health Ave',
  clinicCity: 'Noida',
}

function renderSearch() {
  return render(<DiscoverySearch />, { wrapper: MemoryRouter })
}

describe('DiscoverySearch', () => {
  beforeEach(() => {
    mockedSearchDiscovery.mockReset()
    mockedListCities.mockReset().mockResolvedValue(['Noida', 'Pune'])
    mockedListSpecializations.mockReset().mockResolvedValue(['Cardiology', 'Dermatology'])
  })

  it('renders the fetched eligible-only result list with no login prompt anywhere', async () => {
    mockedSearchDiscovery.mockResolvedValue([ELIGIBLE_RESULT])

    renderSearch()

    await waitFor(() => expect(mockedSearchDiscovery).toHaveBeenCalled())
    expect(await screen.findByText('Dr. Asha Rao')).toBeInTheDocument()
    expect(screen.getByText('Sunrise Clinic')).toBeInTheDocument()
    expect(screen.getByText(/8 years experience/i)).toBeInTheDocument()

    expect(screen.queryByText(/log in/i)).not.toBeInTheDocument()
    expect(screen.queryByText(/sign in/i)).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/password/i)).not.toBeInTheDocument()
  })

  it('narrows the rendered list as the user types a search term', async () => {
    mockedSearchDiscovery.mockResolvedValueOnce([ELIGIBLE_RESULT])
    renderSearch()
    await waitFor(() => expect(mockedSearchDiscovery).toHaveBeenCalledTimes(1))
    await screen.findByText('Dr. Asha Rao')

    mockedSearchDiscovery.mockResolvedValueOnce([ELIGIBLE_RESULT])
    const input = screen.getByLabelText(/search by doctor or clinic name/i)
    await userEvent.type(input, 'Cardiology')

    await waitFor(() =>
      expect(mockedSearchDiscovery).toHaveBeenCalledWith(expect.objectContaining({ q: 'Cardiology' })),
    )
  })

  it('shows an empty state when a search term matches nothing', async () => {
    mockedSearchDiscovery.mockResolvedValueOnce([ELIGIBLE_RESULT])
    renderSearch()
    await screen.findByText('Dr. Asha Rao')

    mockedSearchDiscovery.mockResolvedValueOnce([])
    const input = screen.getByLabelText(/search by doctor or clinic name/i)
    await userEvent.type(input, 'doesnotmatchanything')

    expect(await screen.findByText(/no matching clinics or doctors found/i)).toBeInTheDocument()
  })

  it('sends the selected city as a filter param, scoping results to it', async () => {
    mockedSearchDiscovery.mockResolvedValue([ELIGIBLE_RESULT])
    renderSearch()
    await waitFor(() => expect(mockedListCities).toHaveBeenCalled())
    await screen.findByText('Dr. Asha Rao')

    await userEvent.selectOptions(screen.getByLabelText(/filter by city/i), 'Noida')

    await waitFor(() => expect(mockedSearchDiscovery).toHaveBeenLastCalledWith(expect.objectContaining({ city: 'Noida' })))
  })

  it('sends the selected sort option', async () => {
    mockedSearchDiscovery.mockResolvedValue([ELIGIBLE_RESULT])
    renderSearch()
    await screen.findByText('Dr. Asha Rao')

    await userEvent.selectOptions(screen.getByLabelText(/sort results/i), 'experienceYears:desc')

    await waitFor(() =>
      expect(mockedSearchDiscovery).toHaveBeenLastCalledWith(
        expect.objectContaining({ sort: 'experienceYears', direction: 'desc' }),
      ),
    )
  })

  it('sends the selected minimum experience as a filter param', async () => {
    mockedSearchDiscovery.mockResolvedValue([ELIGIBLE_RESULT])
    renderSearch()
    await screen.findByText('Dr. Asha Rao')

    await userEvent.selectOptions(screen.getByLabelText(/filter by experience/i), '5')

    await waitFor(() =>
      expect(mockedSearchDiscovery).toHaveBeenLastCalledWith(expect.objectContaining({ minExperienceYears: 5 })),
    )
  })
})
