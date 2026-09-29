import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { FormField } from '../../src/components/FormField'

// 054-forms-validation-consistency T002: extracted from 2 real, already-duplicated
// implementations (SignupForm.tsx/RegistrationForm.tsx) - these tests pin the exact behavior
// both already relied on.
describe('FormField', () => {
  it('renders the label and its children', () => {
    render(
      <FormField label="Email" htmlFor="email">
        <input id="email" />
      </FormField>,
    )

    expect(screen.getByText('Email')).toBeInTheDocument()
    expect(screen.getByRole('textbox')).toBeInTheDocument()
  })

  it('shows the hint when there is no error', () => {
    render(
      <FormField label="Mobile" htmlFor="mobile" hint="10-digit Indian mobile number, e.g. 9876543210">
        <input id="mobile" />
      </FormField>,
    )

    expect(screen.getByText(/10-digit Indian mobile number/)).toBeInTheDocument()
  })

  it('shows the error instead of the hint when both are present', () => {
    render(
      <FormField label="Mobile" htmlFor="mobile" hint="10-digit Indian mobile number" error="Invalid mobile number.">
        <input id="mobile" />
      </FormField>,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid mobile number.')
    expect(screen.queryByText('10-digit Indian mobile number')).not.toBeInTheDocument()
  })

  it('marks a required field with an asterisk', () => {
    render(
      <FormField label="Name" htmlFor="name" required>
        <input id="name" />
      </FormField>,
    )

    expect(screen.getByText('*')).toBeInTheDocument()
  })
})
