import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useContext, type ReactNode } from 'react'
import { PreferencesProvider, PreferencesContext } from '../PreferencesContext'

const STORAGE_KEY = 'cql-platform-preferences'

function Probe({ onMount }: { onMount?: () => void }) {
  const ctx = useContext(PreferencesContext)
  if (!ctx) return null
  if (onMount) onMount()
  return (
    <div>
      <div data-testid="font">{ctx.preferences.editorFontSize}</div>
      <div data-testid="theme">{ctx.preferences.themeMode}</div>
      <button onClick={() => ctx.updatePreferences({ editorFontSize: 18 })}>
        bigger
      </button>
      <button onClick={() => ctx.resetPreferences()}>reset</button>
    </div>
  )
}

function withProvider(ui: ReactNode) {
  return <PreferencesProvider>{ui}</PreferencesProvider>
}

describe('PreferencesContext — PAT-149', () => {
  beforeEach(() => {
    localStorage.clear()
  })
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('returns defaults when storage is empty', () => {
    render(withProvider(<Probe />))
    expect(screen.getByTestId('font').textContent).toBe('14')
    expect(screen.getByTestId('theme').textContent).toBe('light')
  })

  it('loads stored preferences on mount', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ editorFontSize: 22 }))
    render(withProvider(<Probe />))
    expect(screen.getByTestId('font').textContent).toBe('22')
    // Unspecified preference still falls back to default
    expect(screen.getByTestId('theme').textContent).toBe('light')
  })

  it('drops corrupt JSON in storage and falls back to defaults', () => {
    localStorage.setItem(STORAGE_KEY, '{not valid')
    render(withProvider(<Probe />))
    expect(screen.getByTestId('font').textContent).toBe('14')
  })

  it('updatePreferences writes to storage', async () => {
    const user = userEvent.setup()
    render(withProvider(<Probe />))
    await user.click(screen.getByText('bigger'))

    expect(screen.getByTestId('font').textContent).toBe('18')
    const stored = JSON.parse(localStorage.getItem(STORAGE_KEY)!)
    expect(stored.editorFontSize).toBe(18)
  })

  it('PAT-149 regression: setItem failure does not crash updatePreferences', async () => {
    // Simulate Safari Private mode quota=0 — setItem throws. jsdom's
    // localStorage doesn't always proxy through Storage.prototype, so we
    // patch the instance method directly and restore in finally.
    const original = window.localStorage.setItem.bind(window.localStorage)
    const setItem = vi.fn(() => {
      throw new DOMException('QuotaExceededError', 'QuotaExceededError')
    })
    Object.defineProperty(window.localStorage, 'setItem', {
      value: setItem,
      configurable: true,
    })

    try {
      const user = userEvent.setup()
      render(withProvider(<Probe />))
      await user.click(screen.getByText('bigger'))

      // In-memory state still updates (best-effort write — safeStorage swallows the throw).
      expect(screen.getByTestId('font').textContent).toBe('18')
      expect(setItem).toHaveBeenCalled()
    } finally {
      Object.defineProperty(window.localStorage, 'setItem', {
        value: original,
        configurable: true,
      })
    }
  })

  it('resetPreferences clears storage and reverts to defaults', async () => {
    const user = userEvent.setup()
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ editorFontSize: 22 }))
    render(withProvider(<Probe />))
    expect(screen.getByTestId('font').textContent).toBe('22')

    await user.click(screen.getByText('reset'))
    expect(screen.getByTestId('font').textContent).toBe('14')
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull()
  })
})
