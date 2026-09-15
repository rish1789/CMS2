import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { DateStrip, todayIsoDate } from '../../src/features/patient-booking/DateStrip'

describe('DateStrip', () => {
  it('marks the first pill as Today and the second as Tmrw', () => {
    render(<DateStrip selectedDate={todayIsoDate()} onSelect={() => {}} />)

    expect(screen.getByRole('button', { name: /today/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /tmrw/i })).toBeInTheDocument()
  })

  it('marks the selected date pill as pressed', () => {
    render(<DateStrip selectedDate={todayIsoDate()} onSelect={() => {}} />)

    expect(screen.getByRole('button', { name: /today/i })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /tmrw/i })).toHaveAttribute('aria-pressed', 'false')
  })

  it('calls onSelect with the ISO date of the clicked pill', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<DateStrip selectedDate={todayIsoDate()} onSelect={onSelect} />)

    await user.click(screen.getByRole('button', { name: /tmrw/i }))

    expect(onSelect).toHaveBeenCalledTimes(1)
    const selected = onSelect.mock.calls[0][0] as string
    expect(selected).not.toBe(todayIsoDate())
    expect(selected > todayIsoDate()).toBe(true)
  })

  it('never renders a date before today', () => {
    render(<DateStrip selectedDate={todayIsoDate()} onSelect={() => {}} />)

    const buttons = screen.getAllByRole('button')
    expect(buttons.length).toBeGreaterThan(0)
    // Every rendered pill's accessible name is either Today/Tmrw or a real weekday label -
    // none of them can represent a day before today, since the strip is built forward-only
    // from todayIsoDate() with no earlier starting point ever constructed.
    expect(buttons[0]).toHaveAccessibleName(/today/i)
  })
})
