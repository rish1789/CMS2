import { useState } from 'react'
import { Link } from 'react-router-dom'
import { IconBadge, ClinicIcon } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'
import { SearchIcon } from '../../components/staffIcons'
import { NextAppointmentCard } from '../../features/patient-bookings/NextAppointmentCard'
import { deriveDisplayNameFromEmail, loadPatientSession } from '../../features/patient-account/token'

// 056-design-copy-quality-pass (2026-09-16 dashboard rebuild): order and copy match
// design/patient-dashboard-reference.html's structure (Find a doctor, My bookings, My
// clinics); descriptions kept close to this project's own already-specific existing copy
// (never flagged as a "childish copy" problem) rather than a full rewrite.
const TILES = [
  {
    title: 'Find a doctor',
    description: 'Search verified clinics and doctors by specialization, name, or location.',
    to: '/discover',
    icon: <SearchIcon />,
  },
  {
    title: 'My bookings',
    description: 'Your upcoming visits, queue position, and waitlist status — all in one place.',
    to: '/patient/bookings',
    icon: <BookingIcon />,
  },
  {
    title: 'My clinics',
    description: "Clinics you've visited before. Book again or join a queue in one tap.",
    to: '/patient/clinics',
    icon: <ClinicIcon />,
  },
]

export function PatientDashboard() {
  const [session] = useState(() => loadPatientSession())
  const displayName = session ? deriveDisplayNameFromEmail(session.email) : ''

  return (
    <div>
      <h1 className="text-2xl font-bold tracking-tight text-gray-900 sm:text-3xl">
        Welcome back{displayName ? `, ${displayName}` : ''}.
      </h1>
      <p className="mt-2 mb-8 max-w-xl text-base text-gray-600">
        Find a doctor, manage your bookings, or check your waitlist status.
      </p>

      <NextAppointmentCard />

      <div className="grid grid-cols-1 gap-5 sm:grid-cols-3">
        {TILES.map((tile) => (
          <Link
            key={tile.title}
            to={tile.to}
            className="group block rounded-2xl border border-gray-200 bg-white p-6 transition-all duration-150 ease-out hover:-translate-y-0.5 hover:shadow-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            <IconBadge>{tile.icon}</IconBadge>
            <h2 className="mt-4 flex items-center justify-between text-base font-semibold text-gray-900">
              {tile.title}
              <span
                aria-hidden="true"
                className="font-normal text-gray-400 transition-all duration-150 ease-out group-hover:translate-x-0.5 group-hover:text-indigo-600"
              >
                →
              </span>
            </h2>
            <p className="mt-2 text-sm text-gray-500">{tile.description}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
