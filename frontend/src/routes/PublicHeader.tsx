import { Link } from 'react-router-dom'

// Shared header for standalone public pages (register, discover, login, signup) that render
// outside any of the three app shells (patient/staff/admin) - without it, these pages were
// dead ends with no in-UI way back to the home page, only the browser's own back button.
export function PublicHeader() {
  return (
    <header className="border-b border-gray-200 bg-white px-6 py-4">
      <Link
        to="/"
        className="inline-flex items-center gap-2 text-sm font-semibold text-gray-900 transition-colors hover:text-indigo-600"
      >
        <span className="flex h-7 w-7 items-center justify-center rounded-lg bg-cobalt-600 text-xs font-bold text-white">
          C
        </span>
        CMS2 Clinic Management
      </Link>
    </header>
  )
}
