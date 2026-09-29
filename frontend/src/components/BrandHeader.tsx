import { Link } from 'react-router-dom'

// 056-design-copy-quality-pass (landing/login rebuild): shared chrome for the public-facing
// marketing surfaces (HomePage, patient login/signup) so they read as one consistent brand
// instead of each page rolling its own header. Intentionally not PublicHeader.tsx (used by
// register/discover/staff-login/etc.) - those are quieter operational pages that only need a
// way back home, not the single "Clinic login" nav item this brand-forward chrome carries.
export function BrandHeader() {
  return (
    <header className="border-b border-gray-200 bg-white px-6 py-5 sm:px-8">
      <div className="mx-auto flex max-w-6xl items-center justify-between">
        <Link to="/" className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900">
          <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
            C
          </span>
          CMS2 Clinic Management
        </Link>
        <Link
          to="/staff/login"
          className="rounded px-1 py-1 text-sm font-medium text-gray-600 transition-colors duration-150 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Clinic login
        </Link>
      </div>
    </header>
  )
}
