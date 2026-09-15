import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { ActionMenu } from '../../src/components/ActionMenu'

// 042-day-sheet-hardening FR-008/FR-009: the same exclusivity + off-screen-clipping bugs
// already found and fixed once this session on Roster's row-actions kebab.
describe('ActionMenu', () => {
  it('wires shared-group instances to the same name attribute (exclusivity itself is a jsdom gap - see note below)', async () => {
    // Cross-instance exclusivity for <details name="..."> is a real, working native browser
    // behavior (confirmed visually on Roster's equivalent fix), but empirically unsupported by
    // jsdom as of this test suite's version - opening a second same-named <details> here does
    // NOT close the first, unlike in a real browser. What *is* verifiable here, and what this
    // fix's own correctness actually depends on, is that both instances share the exact same
    // `name` - the browser's native exclusivity does the rest. Actual "only one open" behavior
    // is covered by quickstart.md Scenario 4 (manual, real-browser verification).
    render(
      <>
        <ActionMenu label="More (row 1)" name="test-group">
          <a href="/one">Item one</a>
        </ActionMenu>
        <ActionMenu label="More (row 2)" name="test-group">
          <a href="/two">Item two</a>
        </ActionMenu>
      </>,
    )

    const [firstDetails, secondDetails] = screen.getAllByText(/^More \(row/).map((el) => el.closest('details'))
    expect(firstDetails).toHaveAttribute('name', 'test-group')
    expect(secondDetails).toHaveAttribute('name', 'test-group')
  })

  it("menus in different exclusivity groups don't affect each other", async () => {
    const user = userEvent.setup()
    render(
      <>
        <ActionMenu label="More (session A)" name="session-a">
          <a href="/one">Item one</a>
        </ActionMenu>
        <ActionMenu label="More (session B)" name="session-b">
          <a href="/two">Item two</a>
        </ActionMenu>
      </>,
    )

    await user.click(screen.getByText('More (session A)'))
    await user.click(screen.getByText('More (session B)'))

    const firstDetails = screen.getByText('More (session A)').closest('details')
    expect((firstDetails as HTMLDetailsElement).open).toBe(true)
  })

  it('renders the popup via a portal, outside the component\'s own DOM subtree', async () => {
    const user = userEvent.setup()
    const { container } = render(
      <ActionMenu label="More" name="test-group">
        <a href="/one">Item one</a>
      </ActionMenu>,
    )

    await user.click(screen.getByText('More'))

    const item = screen.getByText('Item one')
    expect(container.contains(item)).toBe(false)
    expect(document.body.contains(item)).toBe(true)
  })

  // staff-console-audit-2026-09-10 P3: the popup used to be positioned once on open and never
  // again, so scrolling or resizing the page left it stranded away from its trigger. jsdom has
  // no layout engine (getBoundingClientRect/offsetHeight are always 0 - the same gap documented
  // elsewhere this session), so the actual repositioned coordinates aren't verifiable here; what
  // this test can and does verify is that the fix is actually wired up - listeners attached only
  // while open, torn down on close - which real-browser scroll/resize repositioning depends on.
  it('listens for scroll and resize only while open, and stops listening once closed', async () => {
    const user = userEvent.setup()
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    render(
      <ActionMenu label="More" name="test-group">
        <a href="/one">Item one</a>
      </ActionMenu>,
    )

    expect(addSpy).not.toHaveBeenCalledWith('scroll', expect.any(Function), true)

    await user.click(screen.getByText('More'))

    expect(addSpy).toHaveBeenCalledWith('scroll', expect.any(Function), true)
    expect(addSpy).toHaveBeenCalledWith('resize', expect.any(Function))

    await user.click(screen.getByText('More'))

    expect(removeSpy).toHaveBeenCalledWith('scroll', expect.any(Function), true)
    expect(removeSpy).toHaveBeenCalledWith('resize', expect.any(Function))

    addSpy.mockRestore()
    removeSpy.mockRestore()
  })
})
