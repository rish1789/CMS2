import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { HomePage } from '../../src/routes/HomePage'

function renderHomePage() {
  render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  )
}

// 056-design-copy-quality-pass (2026-09-16 landing-page rebuild): patient booking is now the
// dominant hero content; clinic-side access is exactly one low-emphasis "Clinic login" entry
// (header + footer), both pointing at /staff/login - which already role-routes Super Admin
// sign-in (decideClinicPortalDestination) and is where clinic registration now lives (see
// StaffLoginPage.test.tsx-equivalent coverage), so neither a separate admin link nor a
// separate "Register your clinic" card exists on this page anymore.
describe('HomePage', () => {
  it('renders the patient hero with working links to patient login, signup, and discovery', () => {
    renderHomePage()

    expect(screen.getByRole('heading', { level: 1, name: 'One account, every clinic visit.' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/patient/login')
    expect(screen.getByRole('link', { name: 'Create account' })).toHaveAttribute('href', '/patient/signup')
    expect(screen.getByRole('link', { name: /Looking for a doctor\?/ })).toHaveAttribute('href', '/discover')
  })

  it('exposes exactly one "Clinic login" entry point, in the header and footer, pointing at /staff/login', () => {
    renderHomePage()

    const clinicLoginLinks = screen.getAllByRole('link', { name: 'Clinic login' })
    expect(clinicLoginLinks).toHaveLength(2)
    for (const link of clinicLoginLinks) {
      expect(link).toHaveAttribute('href', '/staff/login')
    }
  })

  it('has no separate top-level link to clinic registration or the Super Admin console', () => {
    renderHomePage()

    expect(screen.queryByRole('link', { name: /register/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /super admin/i })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /admin console/i })).not.toBeInTheDocument()
  })

  it('names three real, already-shipped features in the trust section', () => {
    renderHomePage()

    expect(screen.getByRole('heading', { name: 'See your place in line' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'One account, every clinic' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Verified clinics only' })).toBeInTheDocument()
  })
})
