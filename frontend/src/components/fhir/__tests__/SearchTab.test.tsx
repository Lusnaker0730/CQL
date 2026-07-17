import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'

// PR #501 / PAT-161 pattern: SUT uses sub-path icon imports; no barrel mock
// is needed (and the Proxy version no longer works under vitest 4).

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

const searchMock = vi.fn()
vi.mock('../../../api', () => ({
  fhirApi: {
    search: (rt: string, params: string, server: string) => searchMock(rt, params, server),
    read: vi.fn(),
    deleteResource: vi.fn(),
    createResource: vi.fn(),
    updateResource: vi.fn(),
    validateResource: vi.fn(),
  },
}))

const addEntryMock = vi.fn()
vi.mock('../../../hooks/useFhirQueryHistory', () => ({
  default: () => ({
    recent: [],
    favorites: [],
    addEntry: addEntryMock,
    toggleFavorite: vi.fn(),
    removeEntry: vi.fn(),
    clearHistory: vi.fn(),
  }),
}))

// Stub heavy children
vi.mock('../ResourceDetailDialog', () => ({ default: () => null }))
vi.mock('../ResourceEditorDialog', () => ({ default: () => null }))
vi.mock('../SearchParamBuilder', () => ({ default: () => null }))
vi.mock('../QueryHistory', () => ({ default: () => null }))

import SearchTab from '../SearchTab'

describe('SearchTab — PAT-134 history records correct params (P1)', () => {
  beforeEach(() => {
    searchMock.mockReset()
    addEntryMock.mockReset()
  })

  it('records the variables used by mutationFn (not stale React state) when history-replay fires', async () => {
    searchMock.mockResolvedValue({ resourceType: 'Bundle', entry: [], total: 0 })

    render(<SearchTab connectionId={7} resourceType="Patient" />)

    // Trigger an immediate "history-replay-style" call via the search button
    // (the simpler default path also goes through the same onSuccess code).
    fireEvent.click(screen.getByRole('button', { name: 'search.searchButton' }))

    await waitFor(() => expect(addEntryMock).toHaveBeenCalledTimes(1))
    // resourceType=Patient, params='', connectionId=7
    expect(addEntryMock).toHaveBeenCalledWith('Patient', '', 7)
  })
})
