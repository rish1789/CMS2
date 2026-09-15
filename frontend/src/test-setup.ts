import '@testing-library/jest-dom/vitest'

// jsdom parses <dialog> but doesn't implement its imperative API (showModal/close) or its native
// Escape-to-close behavior - https://github.com/jsdom/jsdom/issues/3294. Polyfilled here so
// components built on native <dialog> (DeleteConfirmModal, RejectConfirmModal, EmployeeModal)
// behave the same under vitest as in a real browser.
if (typeof HTMLDialogElement !== 'undefined' && !HTMLDialogElement.prototype.showModal) {
  HTMLDialogElement.prototype.showModal = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.show = function (this: HTMLDialogElement) {
    this.setAttribute('open', '')
  }
  HTMLDialogElement.prototype.close = function (this: HTMLDialogElement) {
    if (!this.hasAttribute('open')) return
    this.removeAttribute('open')
    this.dispatchEvent(new Event('close'))
  }

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return
    const openDialogs = document.querySelectorAll('dialog[open]')
    const topmost = openDialogs[openDialogs.length - 1] as HTMLDialogElement | undefined
    if (!topmost) return
    const cancelEvent = new Event('cancel', { cancelable: true })
    topmost.dispatchEvent(cancelEvent)
    if (!cancelEvent.defaultPrevented) topmost.close()
  })
}
