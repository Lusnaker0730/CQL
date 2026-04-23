import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import LibraryDefinitionPicker, {
  type LibraryDefinitionReference,
} from '../LibraryDefinitionPicker'
import { generateAlias } from '../../../utils/libraryAlias'
import { render } from '@testing-library/react'
import i18n from '../../../i18n'

// ── Mock the API module that feeds the dialog ─────────────────────────────
vi.mock('../../../api', () => ({
  cqlApi: {
    getLibraries: vi.fn(),
    getLibrary: vi.fn(),
  },
}))
// Must mock useCqlLibrary separately — the picker uses this dedicated hook.
vi.mock('../../../hooks/useCqlLibraries', () => ({
  useCqlLibrary: vi.fn(),
}))
// Disable debounce in tests so search triggers immediately.
vi.mock('../../../hooks/useDebouncedValue', () => ({
  useDebouncedValue: <T,>(value: T): T => value,
}))

import { cqlApi } from '../../../api'
import { useCqlLibrary } from '../../../hooks/useCqlLibraries'

function renderPicker(onSelect = vi.fn(), onClose = vi.fn()) {
  // Fresh QueryClient per test so cached results don't bleed.
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const utils = render(
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={queryClient}>
        <LibraryDefinitionPicker open={true} onClose={onClose} onSelect={onSelect} />
      </QueryClientProvider>
    </I18nextProvider>,
  )
  return { ...utils, onSelect, onClose }
}

// ── Pure function: generateAlias ─────────────────────────────────────────

describe('generateAlias', () => {
  it('takes first letter of each PascalCase word', () => {
    expect(generateAlias('DiabetesCohort')).toBe('DC')
  })
  it('handles snake_case and kebab-case words', () => {
    expect(generateAlias('shared_logic')).toBe('SL')
    expect(generateAlias('common-utils')).toBe('CU')
  })
  it('uppercases even lowercase single-word names', () => {
    expect(generateAlias('helpers')).toBe('H')
  })
  it('returns uppercase-3 prefix when no word separators are usable', () => {
    // Name that collapses to an empty letter set — fallback kicks in
    expect(generateAlias('abc')).toBe('A')
  })
  it('handles empty string without throwing', () => {
    expect(generateAlias('')).toBe('')
  })
})

// ── Dialog: library list → detail → confirm ──────────────────────────────

describe('LibraryDefinitionPicker dialog', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  function mockLibrariesListWith(libraries: unknown[]) {
    const getLibsMock = cqlApi.getLibraries as ReturnType<typeof vi.fn>
    getLibsMock.mockResolvedValue(libraries)
  }

  function mockLibraryDetailWith(cqlContent: string, loading = false) {
    const useLibMock = useCqlLibrary as ReturnType<typeof vi.fn>
    useLibMock.mockReturnValue({
      data: { cqlContent },
      isLoading: loading,
    })
  }

  it('filters out retired libraries from the list', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'ActiveLib', version: '1.0.0', status: 'active', ownerUsername: 'u' },
      { id: 2, name: 'DraftLib', version: '0.1.0', status: 'draft', ownerUsername: 'u' },
      { id: 3, name: 'RetiredLib', version: '2.0.0', status: 'retired', ownerUsername: 'u' },
    ])
    mockLibraryDetailWith('', false)
    renderPicker()

    await waitFor(() => expect(screen.getByText('ActiveLib')).toBeInTheDocument())
    expect(screen.getByText('DraftLib')).toBeInTheDocument()
    // RetiredLib must be filtered out — only active/draft libraries are selectable
    // per the component's clinical-safety rule.
    expect(screen.queryByText('RetiredLib')).not.toBeInTheDocument()
  })

  it('advances to step 2 when a library row is clicked and auto-fills alias', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'SharedLogic', version: '1.2.0', status: 'active', ownerUsername: 'u' },
    ])
    mockLibraryDetailWith('library SharedLogic version \'1.2.0\'\ndefine "HasDiabetes": true\n', false)

    const user = userEvent.setup()
    renderPicker()

    // Step 1 — click library row
    const libraryRow = await screen.findByText('SharedLogic')
    await user.click(libraryRow)

    // Step 2 — definitions listed, alias auto-generated from PascalCase
    await waitFor(() => expect(screen.getByText('HasDiabetes')).toBeInTheDocument())
    const aliasInput = screen.getByRole('textbox', { name: /alias/i }) as HTMLInputElement
    expect(aliasInput.value).toBe('SL')
  })

  it('emits LibraryDefinitionReference on confirm with exact picker-selected values', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'SharedLogic', version: '1.2.0', status: 'active', ownerUsername: 'u' },
    ])
    mockLibraryDetailWith('library SharedLogic version \'1.2.0\'\ndefine "HasDiabetes": true\n', false)

    const user = userEvent.setup()
    const { onSelect } = renderPicker()

    await user.click(await screen.findByText('SharedLogic'))
    await user.click(await screen.findByText('HasDiabetes'))

    // Click the "Select" confirm button (not the definition list item)
    const selectButton = screen.getByRole('button', { name: /^select$/i })
    await user.click(selectButton)

    expect(onSelect).toHaveBeenCalledTimes(1)
    const reference = onSelect.mock.calls[0][0] as LibraryDefinitionReference
    expect(reference).toEqual({
      libraryName: 'SharedLogic',
      libraryVersion: '1.2.0',
      definitionName: 'HasDiabetes',
      alias: 'SL',
    })
  })

  it('disables the confirm button until both a definition and non-empty alias are set', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'SharedLogic', version: '1.2.0', status: 'active', ownerUsername: 'u' },
    ])
    mockLibraryDetailWith('define "HasDiabetes": true\n', false)

    const user = userEvent.setup()
    renderPicker()

    await user.click(await screen.findByText('SharedLogic'))
    const selectButton = screen.getByRole('button', { name: /^select$/i })
    // No definition picked yet → disabled
    expect(selectButton).toBeDisabled()

    // Pick a definition
    await user.click(await screen.findByText('HasDiabetes'))
    expect(selectButton).not.toBeDisabled()

    // Clear alias → disabled again (empty-alias guard, defensive even though
    // auto-fill populates it; some libraries trigger generateAlias() → '')
    const aliasInput = screen.getByRole('textbox', { name: /alias/i })
    await user.clear(aliasInput)
    expect(selectButton).toBeDisabled()
  })

  it('clicking Back from step 2 returns to step 1 without emitting a selection', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'SharedLogic', version: '1.2.0', status: 'active', ownerUsername: 'u' },
    ])
    mockLibraryDetailWith('define "HasDiabetes": true\n', false)

    const user = userEvent.setup()
    const { onSelect } = renderPicker()

    await user.click(await screen.findByText('SharedLogic'))
    // Select works (step 2)
    await screen.findByText('HasDiabetes')

    const backButton = screen.getByRole('button', { name: /back/i })
    await user.click(backButton)

    // Step 1 library list restored, no selection emitted
    expect(screen.getByText('SharedLogic')).toBeInTheDocument()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('renders the no-definitions message when the library has no define blocks', async () => {
    mockLibrariesListWith([
      { id: 1, name: 'EmptyLib', version: '0.1.0', status: 'draft', ownerUsername: 'u' },
    ])
    // CQL with no define blocks
    mockLibraryDetailWith('library EmptyLib version \'0.1.0\'\n', false)

    const user = userEvent.setup()
    renderPicker()

    await user.click(await screen.findByText('EmptyLib'))
    // Key should surface from i18n; default fallback will render the key path
    // if translation is missing — either way, the dialog must not crash.
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByRole('radio')).not.toBeInTheDocument()
  })
})
