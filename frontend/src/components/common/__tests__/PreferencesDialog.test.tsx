import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '../../../test/test-utils'

// Stub heavy MUI icon barrel.
// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel
// mock needed (and the Proxy version no longer works under vitest 4).

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

vi.mock('../FhirServerUrlField', () => ({
  default: () => <div data-testid="stub-fhir-field" />,
}))

vi.mock('../../../hooks/usePreferences', () => ({
  usePreferences: () => ({
    preferences: {
      editorFontSize: 14,
      editorTabSize: 2,
      editorWordWrap: 'on',
      editorMinimap: true,
      themeMode: 'light',
      defaultFhirServerUrl: '',
    },
    updatePreferences: vi.fn(),
    resetPreferences: vi.fn(),
  }),
}))

const getVsacStatusMock = vi.fn()
const getAiStatusMock = vi.fn()
const updateVsacApiKeyMock = vi.fn()
const updateAiApiKeyMock = vi.fn()

vi.mock('../../../api/settingsApi', () => ({
  settingsApi: {
    getVsacStatus: () => getVsacStatusMock(),
    getAiStatus: () => getAiStatusMock(),
    updateVsacApiKey: (k: string) => updateVsacApiKeyMock(k),
    updateAiApiKey: (k: string) => updateAiApiKeyMock(k),
  },
}))

import PreferencesDialog from '../PreferencesDialog'

describe('PreferencesDialog — PAT-133 API key lifecycle (P0/P1)', () => {
  beforeEach(() => {
    getVsacStatusMock.mockReset()
    getAiStatusMock.mockReset()
    updateVsacApiKeyMock.mockReset()
    updateAiApiKeyMock.mockReset()
    getVsacStatusMock.mockResolvedValue({ configured: false, url: 'https://vsac.example' })
    getAiStatusMock.mockResolvedValue({ enabled: false, provider: 'cloud' })
  })

  it('clears the typed VSAC key when the dialog closes (P0: no leakage between sessions)', async () => {
    const onClose = vi.fn()
    const { rerender } = render(<PreferencesDialog open={true} onClose={onClose} />)

    const apiKeyField = await screen.findByLabelText('preferences.vsacApiKey')
    fireEvent.change(apiKeyField, { target: { value: 'secret-key-xyz' } })
    expect((apiKeyField as HTMLInputElement).value).toBe('secret-key-xyz')

    // Simulate parent closing the dialog (open=false)
    rerender(<PreferencesDialog open={false} onClose={onClose} />)
    // Re-open it
    rerender(<PreferencesDialog open={true} onClose={onClose} />)

    // The VSAC API key field should be cleared, not retain the previous typed value.
    const reopened = await screen.findByLabelText('preferences.vsacApiKey')
    expect((reopened as HTMLInputElement).value).toBe('')
  })

  it('does not throw if save resolves after the dialog has unmounted (P1: isMountedRef)', async () => {
    let resolveSave: (v: { configured: boolean }) => void = () => {}
    updateVsacApiKeyMock.mockImplementation(
      () => new Promise<{ configured: boolean }>((r) => { resolveSave = r }),
    )

    const { unmount } = render(<PreferencesDialog open={true} onClose={vi.fn()} />)
    const apiKeyField = await screen.findByLabelText('preferences.vsacApiKey')
    fireEvent.change(apiKeyField, { target: { value: 'k' } })

    // Click save → mutation in-flight
    fireEvent.click(screen.getByRole('button', { name: 'preferences.vsacKeySave' }))
    expect(updateVsacApiKeyMock).toHaveBeenCalledWith('k')

    // Unmount mid-flight, then resolve
    unmount()
    resolveSave({ configured: true })
    // Wait a tick — if the guard is missing, React would warn about setState
    // on unmounted component. We just want this not to throw.
    await new Promise((r) => setTimeout(r, 10))
  })
})
