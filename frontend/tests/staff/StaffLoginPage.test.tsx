import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { StaffLoginPage } from '../../src/routes/staff/StaffLoginPage'

// 056-design-copy-quality-pass (landing-page rebuild): clinic registration is no longer a
// top-level HomePage card - it's reachable only from here, the one "Clinic login" entry point.
// StaffLoginForm's own submit/role-routing behavior is covered by StaffLoginForm.test.tsx and
// destination.test.ts; this test covers only the new link this page adds around that form.
describe('StaffLoginPage', () => {
  it('offers a path to clinic registration alongside the sign-in form', () => {
    render(
      <MemoryRouter initialEntries={['/staff/login']}>
        <Routes>
          <Route path="/staff/login" element={<StaffLoginPage />} />
          <Route path="/register" element={<div>Registration page</div>} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: 'Clinic sign in' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Register your clinic' })).toHaveAttribute('href', '/register')
  })
})
