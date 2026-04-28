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

  it('calls onSelect with the most-recent click winning (race-guard)', async () => {
    // First click → slow response; second click → fast response. The user's
    // expectation is that the second click's library is what loads.
    let resolveFirst: (v: { cqlContent: string }) => void = () => {}
    let resolveSecond: (v: { cqlContent: string }) => void = () => {}
    getLibraryMock.mockImplementationOnce(
      () => new Promise<{ cqlContent: string }>((r) => { resolveFirst = r }),
    )
    getLibraryMock.mockImplementationOnce(
      () => new Promise<{ cqlContent: string }>((r) => { resolveSecond = r }),
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
    const B = await screen.findByText('Library B')

    fireEvent.click(A) // dispatches getLibrary('lib-A') — slow
    fireEvent.click(B) // dispatches getLibrary('lib-B') — fast

    // Resolve B first (most-recent click)
    resolveSecond({ cqlContent: 'cql-from-B' })
    await waitFor(() => expect(onSelect).toHaveBeenCalledWith('cql-from-B'))

    // Now resolve A (the superseded request) — should NOT call onSelect again
    resolveFirst({ cqlContent: 'cql-from-A' })
    await new Promise((r) => setTimeout(r, 10))
    expect(onSelect).toHaveBeenCalledTimes(1)
    expect(onSelect).not.toHaveBeenCalledWith('cql-from-A')
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
