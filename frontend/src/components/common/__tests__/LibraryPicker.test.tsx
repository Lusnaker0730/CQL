import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'

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

const getLibrariesMock = vi.fn()
const getLibraryMock = vi.fn()

vi.mock('../../../api', () => ({
  cqlApi: {
    getLibraries: (q?: string) => getLibrariesMock(q),
    getLibrary: (id: string) => getLibraryMock(id),
  },
}))

vi.mock('../../../hooks/useLibraryHistory', () => ({
  useLibraryHistory: () => ({ favoritesList: [], recentList: [] }),
}))

const showNotificationMock = vi.fn()
vi.mock('../../../hooks/useNotification', () => ({
  useNotification: () => ({ showNotification: showNotificationMock }),
}))

import LibraryPicker from '../LibraryPicker'

describe('LibraryPicker — PAT-133 race + error handling (P1)', () => {
  beforeEach(() => {
    getLibrariesMock.mockReset()
    getLibraryMock.mockReset()
    showNotificationMock.mockReset()
  })

  it('blocks concurrent selection while a load is in flight, then selects once (race-guard)', async () => {
    // The list is disabled while a selection is loading (disabled={loading}), so a
    // second click cannot start a competing request — only one getLibrary is ever
    // dispatched, and onSelect fires exactly once with that library. The internal
    // requestTokenRef guard remains as defense-in-depth for supersession the UI
    // can't prevent (e.g. a new search mid-flight).
    let resolveFirst: (v: { cqlContent: string }) => void = () => {}
    getLibraryMock.mockImplementationOnce(
      () => new Promise<{ cqlContent: string }>((r) => { resolveFirst = r }),
    )
    getLibrariesMock.mockResolvedValue([
      { id: 'lib-A', name: 'Library A', version: '1.0' },
      { id: 'lib-B', name: 'Library B', version: '1.0' },
    ])

    const onSelect = vi.fn()
    render(<LibraryPicker open onClose={vi.fn()} onSelect={onSelect} />)

    // Trigger search by typing a query and waiting for debounce (300ms default)
    fireEvent.change(screen.getByPlaceholderText('libraryPicker.searchPlaceholder'), {
      target: { value: 'lib' },
    })
    const A = await screen.findByText('Library A', {}, { timeout: 1500 })
    await screen.findByText('Library B')

    fireEvent.click(A) // dispatches getLibrary('lib-A'); list becomes disabled
    // A second click while loading is a no-op — the list item is now disabled.
    fireEvent.click(screen.getByText('Library B'))
    expect(getLibraryMock).toHaveBeenCalledTimes(1)
    expect(getLibraryMock).toHaveBeenCalledWith('lib-A')

    // Resolving the single in-flight request selects that library exactly once.
    resolveFirst({ cqlContent: 'cql-from-A' })
    await waitFor(() => expect(onSelect).toHaveBeenCalledWith('cql-from-A'))
    expect(onSelect).toHaveBeenCalledTimes(1)
  })

  it('shows an error notification when getLibrary throws (no more silent catch)', async () => {
    getLibrariesMock.mockResolvedValue([{ id: 'lib-X', name: 'Library X', version: '1.0' }])
    getLibraryMock.mockRejectedValue(new Error('not found'))

    render(<LibraryPicker open onClose={vi.fn()} onSelect={vi.fn()} />)

    fireEvent.change(screen.getByPlaceholderText('libraryPicker.searchPlaceholder'), {
      target: { value: 'lib' },
    })
    const item = await screen.findByText('Library X', {}, { timeout: 1500 })
    fireEvent.click(item)

    await waitFor(() =>
      expect(showNotificationMock).toHaveBeenCalledWith('libraryPicker.loadFailed', 'error'),
    )
  })

  it('surfaces an Alert when the search query fails', async () => {
    getLibrariesMock.mockRejectedValue(new Error('500'))

    render(<LibraryPicker open onClose={vi.fn()} onSelect={vi.fn()} />)

    fireEvent.change(screen.getByPlaceholderText('libraryPicker.searchPlaceholder'), {
      target: { value: 'lib' },
    })

    expect(
      await screen.findByText('libraryPicker.searchFailed', {}, { timeout: 1500 }),
    ).toBeInTheDocument()
  })
})
