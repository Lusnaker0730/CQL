import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from '../../../i18n'
import ExecutionErrorAlert from '../ExecutionErrorAlert'
import type { ExecutionErrorInfo } from '../../../types'

function renderWithI18n(ui: React.ReactElement) {
  return render(<I18nextProvider i18n={i18n}>{ui}</I18nextProvider>)
}

describe('ExecutionErrorAlert', () => {
  it('renders phase label, errorType, and message', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_translation',
      errorType: 'CqlExecutionException',
      message: 'Could not resolve call',
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)

    // Phase chip uses the i18n key; default fallback is the raw phase string
    expect(screen.getByText(/CqlExecutionException/)).toBeInTheDocument()
    expect(screen.getByText(/Could not resolve call/)).toBeInTheDocument()
  })

  it('omits stack trace UI when stackTraceSummary is absent', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_execution',
      errorType: 'RuntimeException',
      message: 'boom',
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    // No stack trace label rendered
    expect(screen.queryByText(/Stack trace/i)).not.toBeInTheDocument()
  })

  it('renders stack trace toggle when stackTraceSummary is non-empty', () => {
    const errorInfo: ExecutionErrorInfo = {
      phase: 'cql_execution',
      errorType: 'RuntimeException',
      message: 'boom',
      stackTraceSummary: ['com.cqlplatform.Foo.bar(Foo.java:10)'],
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    expect(screen.getByText(/Stack trace/i)).toBeInTheDocument()
  })

  it('accepts unknown phase values without crashing (falls back to raw string)', () => {
    // If backend ever emits a phase we don't have an i18n key for, i18next returns
    // the defaultValue (the raw phase string) rather than throwing.
    const errorInfo: ExecutionErrorInfo = {
      phase: 'some_future_phase',
      errorType: 'RuntimeException',
      message: 'x',
    }
    renderWithI18n(<ExecutionErrorAlert errorInfo={errorInfo} />)
    expect(screen.getByText(/some_future_phase/)).toBeInTheDocument()
  })
})
