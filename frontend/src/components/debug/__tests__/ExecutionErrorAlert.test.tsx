import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '../../../i18n'
import ExecutionErrorAlert from '../ExecutionErrorAlert'
import type { ExecutionErrorInfo } from '../../../types'

// Tests use translation-independent selectors (errorType, message, unknown-phase
// fallback via i18n defaultValue, DOM role/structure) so they survive both en
// and zh-TW test locales without coupling to specific strings.

function renderWithI18n(ui: React.ReactElement) {
  return render(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>)
}

describe('ExecutionErrorAlert', () => {
  it('renders errorType and message verbatim', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_translation',
      errorType: 'CqlExecutionException',
      message: 'Could not resolve call',
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    expect(screen.getByText(/CqlExecutionException/)).toBeInTheDocument()
    expect(screen.getByText(/Could not resolve call/)).toBeInTheDocument()
  })

  it('omits stack trace UI when stackTraceSummary is absent', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_execution',
      errorType: 'RuntimeException',
      message: 'boom',
    }
    const { container } = renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    // Only one interactive element should exist (none for stack toggle). The
    // alert itself renders no buttons when stack is absent.
    expect(container.querySelectorAll('button').length).toBe(0)
  })

  it('renders stack trace toggle when stackTraceSummary is non-empty', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_execution',
      errorType: 'RuntimeException',
      message: 'boom',
      stackTraceSummary: ['com.cqlplatform.Foo.bar(Foo.java:10)'],
    }
    const { container } = renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    // Stack trace block renders an IconButton toggle — assert via DOM role,
    // not translated label, so the test works under any active locale.
    expect(container.querySelectorAll('button').length).toBeGreaterThan(0)
  })

  it('accepts unknown phase values without crashing (falls back to raw string)', () => {
    // If backend ever emits a phase we don't have an i18n key for, i18next
    // returns the defaultValue (the raw phase string). This is locale-
    // independent because the key is missing in every locale.
    const errorInfo: ExecutionErrorInfo = {
      phase: 'some_future_phase',
      errorType: 'RuntimeException',
      message: 'x',
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    expect(screen.getByText(/some_future_phase/)).toBeInTheDocument()
  })
})
