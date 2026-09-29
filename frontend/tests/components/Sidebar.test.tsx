import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Sidebar, type SidebarNavItem } from '../../src/components/Sidebar'

const ITEMS: SidebarNavItem[] = [
  { to: '/staff/clinics/c1', label: 'Clinic tools home', icon: <span>home-icon</span>, end: true },
  { to: '/staff/clinics/c1/day-sheet', label: 'Day sheet', icon: <span>day-sheet-icon</span> },
  { to: '/staff/clinics/c1/onboard', label: 'Onboard staff', icon: <span>onboard-icon</span>, roles: ['ClinicAdmin'] },
]

function renderSidebar(initialPath: string, activeRole?: SidebarNavItem['roles'][number]) {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Sidebar items={ITEMS} activeRole={activeRole} />
    </MemoryRouter>,
  )
}

// 050-sidebar-navigation T003: confirms Sidebar's own active-state and role-filtering logic in
// isolation, before it's wired into the real ClinicShell/AdminShell (T005/T006/T009).
describe('Sidebar', () => {
  it('renders every item that has no role restriction, regardless of activeRole', () => {
    renderSidebar('/staff/clinics/c1/day-sheet')

    expect(screen.getByRole('link', { name: /Clinic tools home/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Day sheet/ })).toBeInTheDocument()
  })

  it('highlights the item matching the current route as active', () => {
    renderSidebar('/staff/clinics/c1/day-sheet')

    expect(screen.getByRole('link', { name: /Day sheet/ })).toHaveClass('bg-indigo-100')
    expect(screen.getByRole('link', { name: /Clinic tools home/ })).not.toHaveClass('bg-indigo-100')
  })

  it('does not treat the home item as active on a nested route (end match)', () => {
    renderSidebar('/staff/clinics/c1/day-sheet')

    expect(screen.getByRole('link', { name: /Clinic tools home/ })).not.toHaveClass('bg-indigo-100')
  })

  it('hides a role-restricted item when activeRole is not in its allowlist', () => {
    renderSidebar('/staff/clinics/c1/day-sheet', 'Doctor')

    expect(screen.queryByRole('link', { name: /Onboard staff/ })).not.toBeInTheDocument()
  })

  it('shows a role-restricted item when activeRole is in its allowlist', () => {
    renderSidebar('/staff/clinics/c1/day-sheet', 'ClinicAdmin')

    expect(screen.getByRole('link', { name: /Onboard staff/ })).toBeInTheDocument()
  })

  it('hides a role-restricted item when no activeRole is given at all', () => {
    renderSidebar('/staff/clinics/c1/day-sheet')

    expect(screen.queryByRole('link', { name: /Onboard staff/ })).not.toBeInTheDocument()
  })
})
