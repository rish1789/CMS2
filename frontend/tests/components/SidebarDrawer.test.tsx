import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { Sidebar, type SidebarNavItem } from '../../src/components/Sidebar'
import { SidebarDrawer } from '../../src/components/SidebarDrawer'

const ITEMS: SidebarNavItem[] = [
  { to: '/staff/clinics/c1', label: 'Clinic tools home', icon: <span>home-icon</span>, end: true },
  { to: '/staff/clinics/c1/day-sheet', label: 'Day sheet', icon: <span>day-sheet-icon</span> },
]

function renderDrawer() {
  render(
    <MemoryRouter initialEntries={['/staff/clinics/c1/day-sheet']}>
      <SidebarDrawer>
        <Sidebar items={ITEMS} />
      </SidebarDrawer>
    </MemoryRouter>,
  )
}

// 050-sidebar-navigation T004: confirms the responsive collapse mechanics standalone - a
// hamburger-triggered, native-<dialog>-backed drawer (research.md Decision 3), reusing Part 3's
// jsdom dialog polyfill the same way Modal.test.tsx does.
describe('SidebarDrawer', () => {
  it('renders the sidebar content inline and a hamburger trigger, with no drawer open by default', () => {
    renderDrawer()

    expect(screen.getByRole('button', { name: 'Open navigation menu' })).toBeInTheDocument()
    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument()
  })

  it('opens the drawer on hamburger click, listing every item', async () => {
    const user = userEvent.setup()
    renderDrawer()

    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))

    const dialog = screen.getByRole('dialog', { name: 'Navigation menu' })
    expect(within(dialog).getByRole('link', { name: /Clinic tools home/ })).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: /Day sheet/ })).toBeInTheDocument()
  })

  it('closes when the backdrop (the dialog element itself) is clicked', async () => {
    const user = userEvent.setup()
    renderDrawer()
    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))

    await user.click(screen.getByRole('dialog', { name: 'Navigation menu' }))

    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument()
  })

  it('closes when an item inside the drawer is selected', async () => {
    const user = userEvent.setup()
    renderDrawer()
    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))

    const dialog = screen.getByRole('dialog', { name: 'Navigation menu' })
    await user.click(within(dialog).getByRole('link', { name: /Day sheet/ }))

    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument()
  })

  it('closes on its own explicit close button', async () => {
    const user = userEvent.setup()
    renderDrawer()
    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))

    await user.click(screen.getByRole('button', { name: 'Close navigation menu' }))

    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument()
  })

  it('closes on Escape (native dialog behavior, via the existing jsdom polyfill)', async () => {
    const user = userEvent.setup()
    renderDrawer()
    await user.click(screen.getByRole('button', { name: 'Open navigation menu' }))
    expect(screen.getByRole('dialog', { name: 'Navigation menu' })).toBeInTheDocument()

    await user.keyboard('{Escape}')

    expect(screen.queryByRole('dialog', { name: 'Navigation menu' })).not.toBeInTheDocument()
  })
})
