import { Link } from 'react-router-dom'

// See BrandHeader.tsx - the matching footer half of the same shared marketing chrome.
export function BrandFooter() {
  return (
    <footer className="border-t border-gray-200 px-6 py-6 sm:px-8">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 text-sm text-gray-500">
        <span>© 2026 CMS2 Clinic Management</span>
        <Link
          to="/staff/login"
          className="rounded transition-colors duration-150 hover:text-gray-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Clinic login
        </Link>
      </div>
    </footer>
  )
}
