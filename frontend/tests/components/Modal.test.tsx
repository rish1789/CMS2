import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { Modal, type ModalHandle } from '../../src/components/Modal'

// 049-shared-ui-components T003: confirms the shared shell provides the same
// backdrop-click/Escape/imperative-close behavior DeleteConfirmModal/RejectConfirmModal already
// had before migration (Part 3's native <dialog> accessibility hardening, preserved here).
describe('Modal', () => {
  it('opens via showModal on mount and renders children', () => {
    render(
      <Modal onClose={vi.fn()} ariaLabel="Test modal">
        <p>Modal body content</p>
      </Modal>,
    )

    expect(screen.getByText('Modal body content')).toBeInTheDocument()
    expect(screen.getByRole('dialog', { name: 'Test modal' })).toBeInTheDocument()
  })

  it('closes when the backdrop (the dialog element itself, not its content) is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Modal onClose={onClose} ariaLabel="Test modal">
        <p>Modal body content</p>
      </Modal>,
    )

    const dialog = screen.getByRole('dialog', { name: 'Test modal' })
    await user.click(dialog)

    expect(onClose).toHaveBeenCalled()
  })

  it('does not close when content inside the dialog is clicked', async () => {
    const user = userEvent.setup()
    const onClose = vi.fn()
    render(
      <Modal onClose={onClose} ariaLabel="Test modal">
        <p>Modal body content</p>
      </Modal>,
    )

    await user.click(screen.getByText('Modal body content'))

    expect(onClose).not.toHaveBeenCalled()
  })

  it('exposes an imperative close() via ref for a caller-rendered Cancel/X button', () => {
    const ref = createRef<ModalHandle>()
    const onClose = vi.fn()
    render(
      <Modal ref={ref} onClose={onClose} ariaLabel="Test modal">
        <p>Modal body content</p>
      </Modal>,
    )

    ref.current?.close()

    expect(onClose).toHaveBeenCalled()
  })
})
