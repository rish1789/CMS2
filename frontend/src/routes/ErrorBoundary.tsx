import { Component, type ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

// React only catches render errors via a class component's static lifecycle hook - without
// this, any component throwing during render (a null API field a .map() didn't guard, etc.)
// unmounts the whole tree and leaves the user staring at a blank white page with no way back.
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true }
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="flex min-h-screen items-center justify-center bg-gray-50 px-6">
          <div className="mx-auto max-w-md space-y-4 rounded-lg border border-gray-200 bg-white p-6 text-center shadow-sm">
            <h1 className="text-lg font-semibold text-gray-900">Something went wrong</h1>
            <p className="text-sm text-gray-600">
              An unexpected error occurred. Try reloading the page.
            </p>
            <a
              href="/"
              className="inline-block rounded-lg bg-indigo-600 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition-all duration-150 ease-out hover:bg-indigo-500 hover:shadow active:scale-[0.98] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-indigo-500 focus-visible:ring-offset-2"
            >
              Back to home
            </a>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}
