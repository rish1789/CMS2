import { Link } from 'react-router-dom'
import { IconBadge, ClinicIcon } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'
import { SearchIcon } from '../../components/staffIcons'
import { NextAppointmentCard } from '../../features/patient-bookings/NextAppointmentCard'

// patient-booking-flow-rebuild: replaces the previous raw "type a Clinic/Booking/Session ID"
// forms with real browse/pick entry points, mirroring ClinicToolsDashboard's staff-side tile
// shape - the same icon-badge, hover, and active-press treatment used across the app.
const TILES = [
  {
    title: 'Find a doctor',
    description: 'Search verified clinics and doctors by specialization, name, or location.',
    to: '/discover',
    icon: <SearchIcon />,
  },
  {
    title: 'My clinics',
    description: "Clinics you've visited before - book a slot, join a queue, or join a waitlist.",
    to: '/patient/clinics',
    icon: <ClinicIcon />,
  },
  {
    title: 'My bookings',
    description: "Your booked slots, joined queues, and waitlist status - view details, cancel, or claim an offer.",
    to: '/patient/bookings',
    icon: <BookingIcon />,
  },
]

export function PatientDashboard() {
  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Welcome back</h1>
        <p className="mt-0.5 text-sm text-gray-600">Find a doctor, manage your bookings, or check your waitlist status.</p>
      </div>
      <NextAppointmentCard />
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        {TILES.map((tile) => (
          <Link
            key={tile.title}
            to={tile.to}
            className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 shadow-sm transition-all duration-150 ease-out hover:border-indigo-300 hover:shadow-md active:scale-[0.99] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
          >
            <IconBadge>{tile.icon}</IconBadge>
            <div>
              <h3 className="text-sm font-semibold text-gray-900">{tile.title}</h3>
              <p className="mt-1 text-sm text-gray-600">{tile.description}</p>
            </div>
          </Link>
        ))}
      </div>
    </div>
  )
}
