import { Link } from 'react-router-dom'
import { ClinicIcon, CheckIcon, IconBadge } from '../components/adminIcons'
import { ClockIcon } from '../components/staffIcons'
import { BrandHeader } from '../components/BrandHeader'
import { BrandFooter } from '../components/BrandFooter'

// 056-design-copy-quality-pass (2026-09-16 landing-page rebuild): replaces the prior
// four-card role-picker layout. Reference: design/landing-page-reference.html (structure/
// hierarchy only - copy, brand, colors, and font are this project's own, not the reference's
// "Carepoint"/Newsreader/custom teal palette, per PRODUCT.md's existing tone and index.css's
// existing @theme tokens). Patient booking now dominates the hero (this app's primary,
// highest-volume audience); clinic-side access is a single, low-emphasis "Clinic login" link
// (header + footer) pointing at /staff/login, which already role-routes a successful sign-in
// to either /staff or /super-admin-console (decideClinicPortalDestination, 040-super-admin-
// rbac-login) - so Super Admin auth already lives behind that one entry point with no code
// change needed here. Clinic registration (/register) is reachable from that same login page
// (see StaffLoginPage.tsx's new "New clinic?" link) rather than as its own top-level card -
// neither it nor a Super Admin link appears on this page anymore.
const trustItems = [
  {
    icon: <ClockIcon />,
    title: 'See your place in line',
    body: "Track queues and waitlists in real time, and know when it's your turn.",
  },
  {
    icon: <ClinicIcon />,
    title: 'One account, every clinic',
    body: "Visiting a new clinic doesn't mean starting over — your account moves with you.",
  },
  {
    icon: <CheckIcon />,
    title: 'Verified clinics only',
    body: 'Every clinic is reviewed before patients can book with them.',
  },
]

function BookingIllustration() {
  return (
    <svg viewBox="0 0 400 400" className="h-auto w-full max-w-sm" xmlns="http://www.w3.org/2000/svg">
      <circle cx="200" cy="200" r="180" fill="none" className="stroke-gray-200" strokeWidth="1.5" strokeDasharray="2 8" />
      <rect x="90" y="120" width="220" height="170" rx="18" className="fill-white stroke-gray-200" strokeWidth="1.5" />
      <rect x="90" y="120" width="220" height="40" rx="18" className="fill-indigo-600" />
      <rect x="90" y="146" width="220" height="14" className="fill-indigo-600" />
      <circle cx="118" cy="140" r="5" className="fill-white" opacity="0.85" />
      <circle cx="136" cy="140" r="5" className="fill-white" opacity="0.6" />
      <circle cx="154" cy="140" r="5" className="fill-white" opacity="0.4" />
      <g className="stroke-gray-200" strokeWidth="1.2">
        <line x1="112" y1="196" x2="288" y2="196" />
        <line x1="112" y1="222" x2="288" y2="222" />
        <line x1="112" y1="248" x2="230" y2="248" />
      </g>
      <circle cx="112" cy="196" r="4" className="fill-amber-500" />
      <circle cx="112" cy="222" r="4" className="fill-indigo-600" />
      <circle cx="112" cy="248" r="4" className="fill-gray-400" />
      <rect x="185" y="270" width="100" height="32" rx="8" className="fill-indigo-600" />
      <text x="235" y="291" fontSize="12" textAnchor="middle" fontWeight="600" className="fill-white">
        Confirmed
      </text>
      <circle cx="305" cy="118" r="34" className="fill-amber-400" opacity="0.16" />
      <circle cx="80" cy="290" r="24" className="fill-indigo-600" opacity="0.12" />
    </svg>
  )
}

export function HomePage() {
  return (
    <div className="flex min-h-screen flex-col bg-gray-50">
      <BrandHeader />

      <main className="flex flex-1 items-center">
        <div className="mx-auto grid w-full max-w-6xl gap-12 px-6 py-16 sm:px-8 lg:grid-cols-[1.1fr_0.9fr] lg:items-center lg:gap-14 lg:py-20">
          <div>
            <h1 className="max-w-xs text-4xl font-bold tracking-tight text-gray-900 sm:max-w-sm sm:text-5xl">
              One account, every clinic visit.
            </h1>
            <p className="mt-5 max-w-md text-lg text-gray-600">
              Book appointments, skip long queues, and keep every visit organized — all from one
              account, at any clinic you visit.
            </p>
            <div className="mt-9 flex flex-wrap items-center gap-5">
              <Link
                to="/patient/login"
                className="inline-flex items-center justify-center rounded-lg bg-indigo-600 px-7 py-4 text-base font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Log in
              </Link>
              <Link
                to="/patient/signup"
                className="inline-flex items-center justify-center rounded-lg border border-gray-300 px-7 py-4 text-base font-semibold text-gray-900 transition-colors duration-150 hover:bg-gray-100 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-gray-400 focus-visible:ring-offset-2"
              >
                Create account
              </Link>
            </div>
            {/* The <p> (block) carries the vertical spacing; the Link inside stays `inline`
                (not `inline-flex`) so its border-bottom wraps per-line instead of stretching
                to a fixed box width past where the "→" actually ends. `margin-top` has no
                effect on an inline element itself, which is why that spacing lives here. */}
            <p className="mt-9">
              <Link
                to="/discover"
                className="inline border-b border-gray-300 pb-0.5 text-sm font-medium text-gray-500 transition-colors duration-150 hover:border-gray-500 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
              >
                Looking for a doctor? Search without signing in →
              </Link>
            </p>
          </div>

          <div className="hidden justify-self-end lg:block" aria-hidden="true">
            <BookingIllustration />
          </div>
        </div>
      </main>

      <section className="border-t border-gray-200">
        <div className="mx-auto grid max-w-6xl gap-8 px-6 py-12 sm:px-8 sm:grid-cols-3">
          {trustItems.map((item) => (
            <div key={item.title} className="flex items-start gap-3.5">
              <IconBadge>{item.icon}</IconBadge>
              <div>
                <h3 className="text-sm font-semibold text-gray-900">{item.title}</h3>
                <p className="mt-1 text-sm text-gray-500">{item.body}</p>
              </div>
            </div>
          ))}
        </div>
      </section>

      <BrandFooter />
    </div>
  )
}
