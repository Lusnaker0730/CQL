import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'

// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel
// mock needed (and the Proxy version no longer works under vitest 4).

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, opts?: Record<string, unknown>) => {
      if (typeof opts === 'object' && opts && 'defaultValue' in opts) {
        return key
      }
      if (!opts) return key
      return Object.entries(opts).reduce(
        (acc, [k, v]) => acc.replace(new RegExp(`{{${k}}}`, 'g'), String(v)),
        key,
      )
    },
    i18n: { changeLanguage: vi.fn() },
  }),
}))

const revokeMock = vi.fn()

vi.mock('../../../hooks/useCdsHooks', () => ({
  useApiKeys: () => ({
    data: [
      {
        id: 1,
        name: 'EHR Integration',
        keyPreview: 'cqp_****abcd',
        createdAt: '2026-04-01T00:00:00Z',
        lastUsedAt: null,
        active: true,
      },
    ],
    isLoading: false,
    isError: false,
  }),
  useGenerateApiKey: () => ({ mutateAsync: vi.fn(), isPending: false }),
  useRevokeApiKey: () => ({ mutateAsync: revokeMock, isPending: false }),
}))

import ApiKeyManager from '../ApiKeyManager'

describe('ApiKeyManager — PAT-132 revoke flow (P1)', () => {
  beforeEach(() => {
    revokeMock.mockReset()
    revokeMock.mockResolvedValue(undefined)
  })

  it('shows the ConfirmDeleteDialog instead of window.confirm when revoking', () => {
    render(<ApiKeyManager />)

    // Trigger the revoke icon for the only row
    fireEvent.click(screen.getByLabelText('apiKeys.revokeAriaLabel'))

    // The dialog title is the i18n key string under our mock
    expect(screen.getByText('apiKeys.revokeDialogTitle')).toBeInTheDocument()
    // ConfirmDeleteDialog renders a "Cancel" + "Delete" button via the
    // `actions.cancel` / `actions.delete` keys (default ns).
  })

  it('does not call the revoke mutation until the user confirms in the dialog', () => {
    render(<ApiKeyManager />)

    fireEvent.click(screen.getByLabelText('apiKeys.revokeAriaLabel'))
    expect(revokeMock).not.toHaveBeenCalled()
  })

  it('fires the revoke mutation with the row id on confirm', async () => {
    render(<ApiKeyManager />)

    fireEvent.click(screen.getByLabelText('apiKeys.revokeAriaLabel'))

    // Two buttons in the dialog: Cancel + Delete. The destructive one is
    // contained variant — but querying by name is most stable.
    const buttons = screen.getAllByRole('button')
    // The dialog's confirm button uses the i18n key 'actions.delete'
    const confirm = buttons.find((b) => b.textContent === 'actions.delete')
    expect(confirm).toBeDefined()
    fireEvent.click(confirm!)

    await waitFor(() => expect(revokeMock).toHaveBeenCalledTimes(1))
    expect(revokeMock).toHaveBeenCalledWith(1)
  })
})
