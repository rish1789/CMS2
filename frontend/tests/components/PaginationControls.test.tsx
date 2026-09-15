import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { PaginationControls } from '../../src/components/PaginationControls'

describe('PaginationControls', () => {
  it('shows the page position and record range', () => {
    render(<PaginationControls page={1} pageSize={20} totalCount={45} onPageChange={vi.fn()} itemLabel="doctors" />)

    expect(screen.getByText('Page 2 of 3')).toBeInTheDocument()
    expect(screen.getByText('21-40 of 45 doctors')).toBeInTheDocument()
  })

  it('defaults the item label to "Records"', () => {
    render(<PaginationControls page={0} pageSize={20} totalCount={5} onPageChange={vi.fn()} />)

    expect(screen.getByText('1-5 of 5 Records')).toBeInTheDocument()
  })

  it('disables Previous on the first page and Next on the last page', () => {
    render(<PaginationControls page={0} pageSize={20} totalCount={5} onPageChange={vi.fn()} />)

    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('calls onPageChange with the next/previous 0-indexed page', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<PaginationControls page={1} pageSize={20} totalCount={100} onPageChange={onPageChange} />)

    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(2)

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(onPageChange).toHaveBeenCalledWith(0)
  })

  it('jumps to a 1-indexed page typed by the user, converting to 0-indexed', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<PaginationControls page={0} pageSize={20} totalCount={100} onPageChange={onPageChange} />)

    await user.type(screen.getByLabelText('Jump to page'), '3')
    await user.click(screen.getByRole('button', { name: 'Go' }))

    expect(onPageChange).toHaveBeenCalledWith(2)
  })

  it('clamps a jump-to-page target beyond the last page', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    render(<PaginationControls page={0} pageSize={20} totalCount={45} onPageChange={onPageChange} />)

    await user.type(screen.getByLabelText('Jump to page'), '99')
    await user.click(screen.getByRole('button', { name: 'Go' }))

    expect(onPageChange).toHaveBeenCalledWith(2) // totalPages - 1
  })

  it('shows 0-0 of 0 when there are no records at all', () => {
    render(<PaginationControls page={0} pageSize={20} totalCount={0} onPageChange={vi.fn()} itemLabel="patients" />)

    expect(screen.getByText('0-0 of 0 patients')).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1')).toBeInTheDocument()
  })
})
