import { Link, useParams, useSearchParams } from 'react-router-dom'
import { IconBadge, SessionIcon } from '../../components/adminIcons'
import { BookingIcon } from '../../components/patientIcons'
import { ClockIcon } from '../../components/staffIcons'

// patient-booking-flow-rebuild: the per-clinic landing hub reached from "My clinics" or a
// Discovery search result - replaces the dashboard's raw "type a Clinic ID" forms for booking a
// slot, joining a queue, or joining the waitlist. Mirrors ClinicToolsDashboard's staff-side hub
// shape. An optional ?doctorId= (set by a Discovery result for one specific doctor) is passed
// through to both booking tiles so either browse path lands pre-filtered.
export function PatientClinicHubPage() {
  const { clinicId } = useParams<{ clinicId: string }>()
  const [searchParams] = useSearchParams()
  const doctorId = searchParams.get('doctorId')
  const doctorQuery = doctorId ? `?doctorId=${doctorId}` : ''

  if (!clinicId) return null

  const tiles = [
    {
      title: 'Book a slot',
      description: 'Browse open, fixed-time appointment slots and book one directly.',
      to: `/patient/clinics/${clinicId}/book${doctorQuery}`,
      icon: <BookingIcon />,
    },
    {
      title: 'Join a queue',
      description: 'Browse upcoming queue sessions and receive a token number.',
      to: `/patient/clinics/${clinicId}/queue-sessions${doctorQuery}`,
      icon: <SessionIcon />,
    },
    {
      title: 'Join the waitlist',
      description: "No open slot right now? Get notified when one opens up.",
      to: `/patient/clinics/${clinicId}/waitlist/join`,
      icon: <ClockIcon />,
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-lg font-semibold text-gray-900">Book at this clinic</h1>
        <p className="mt-0.5 text-sm text-gray-600">Choose how you'd like to book.</p>
      </div>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
        {tiles.map((tile) => (
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
