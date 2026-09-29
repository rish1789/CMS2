import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ToastProvider, useToast } from '../../src/components/Toast'

function TriggerButton({ message }: { message: string }) {
  const { showToast } = useToast()
  return (
    <button type="button" onClick={() => showToast(message)}>
      Trigger
    </button>
  )
}

// 049-shared-ui-components T010: proves the toast system US2 requires - transient, non-blocking
// feedback distinct from the existing permanent inline role="alert" pattern.
describe('Toast', () => {
  it('shows a toast when showToast is called, and it does not block page interaction', async () => {
    const user = userEvent.setup()
    render(
      <ToastProvider>
        <TriggerButton message="Saved successfully." />
        <button type="button">Other page control</button>
      </ToastProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Trigger' }))

    expect(await screen.findByText('Saved successfully.')).toBeInTheDocument()
    // The rest of the page remains interactive - no full-screen overlay.
    await user.click(screen.getByRole('button', { name: 'Other page control' }))
  })

  it('is dismissible via its own close button', async () => {
    const user = userEvent.setup()
    render(
      <ToastProvider>
        <TriggerButton message="Saved successfully." />
      </ToastProvider>,
    )

    await user.click(screen.getByRole('button', { name: 'Trigger' }))
    await screen.findByText('Saved successfully.')

    await user.click(screen.getByRole('button', { name: 'Dismiss' }))

    await waitFor(() => expect(screen.queryByText('Saved successfully.')).not.toBeInTheDocument())
  })

  it('throws a clear error when useToast is used outside a ToastProvider', () => {
    function Orphan() {
      useToast()
      return null
    }
    // Suppress the expected React error-boundary console noise for this one assertion.
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {})
    expect(() => render(<Orphan />)).toThrow('useToast must be used within a ToastProvider')
    spy.mockRestore()
  })
})
