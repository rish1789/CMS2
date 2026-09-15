import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-6">
      <div className="mx-auto max-w-md space-y-4 rounded-lg border border-gray-200 bg-white p-6 text-center shadow-sm">
        <h1 className="text-lg font-semibold text-gray-900">Page not found</h1>
        <p className="text-sm text-gray-600">
          The page you're looking for doesn't exist or may have been moved.
        </p>
        <Link
          to="/"
          className="inline-block rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
        >
          Back to home
        </Link>
      </div>
    </div>
  )
}
