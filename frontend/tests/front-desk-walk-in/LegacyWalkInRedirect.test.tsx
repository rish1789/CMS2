import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { LegacyWalkInRedirect } from '../../src/routes/staff/ClinicToolPages'

function WhereAmI() {
  const location = useLocation()
  return <p>{`${location.pathname}${location.search}`}</p>
}

// 063-front-desk-walk-in (FR-020, tasks.md T031): the retired per-session walk-in URL lands on the
// front-desk screen with that session pre-selected, so old links and bookmarks keep working.
describe('LegacyWalkInRedirect (063-front-desk-walk-in US4)', () => {
  it('redirects the old per-session walk-in URL to the front-desk screen with the session pre-selected', () => {
    render(
      <MemoryRouter initialEntries={['/staff/clinics/clinic-1/sessions/session-1/walk-in?doctorProfileId=doctor-1']}>
        <Routes>
          <Route path="/staff/clinics/:clinicId/sessions/:sessionId/walk-in" element={<LegacyWalkInRedirect />} />
          <Route path="/staff/clinics/:clinicId/walk-in" element={<WhereAmI />} />
        </Routes>
      </MemoryRouter>,
    )

    expect(screen.getByText('/staff/clinics/clinic-1/walk-in?sessionId=session-1')).toBeInTheDocument()
  })
})
