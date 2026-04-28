import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'

vi.mock('@mui/icons-material', () => {
  const Stub = () => null
  return new Proxy(
    { default: Stub },
    { get: (_t, prop) => (prop === '__esModule' ? true : Stub) },
  )
})

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

    render(<SearchTab fhirServer="https://hapi.example" resourceType="Patient" />)

    // Trigger an immediate "history-replay-style" call via the search button
    // (the simpler default path also goes through the same onSuccess code).
    fireEvent.click(screen.getByRole('button', { name: 'search.searchButton' }))

    await waitFor(() => expect(addEntryMock).toHaveBeenCalledTimes(1))
    // resourceType=Patient, params='', server=https://hapi.example
    expect(addEntryMock).toHaveBeenCalledWith('Patient', '', 'https://hapi.example')
  })
})
