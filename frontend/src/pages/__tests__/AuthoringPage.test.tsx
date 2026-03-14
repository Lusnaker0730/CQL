import { describe, it, expect, vi, beforeEach } from 'vitest'
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { render } from '../../test/test-utils'
import AuthoringPage from '../AuthoringPage'

vi.mock('../../hooks/useAuthoring', () => ({
  useArtifacts: vi.fn(),
  useArtifact: vi.fn(),
  useCreateArtifact: vi.fn(),
  useDeleteArtifact: vi.fn(),
  useDuplicateArtifact: vi.fn(),
}))

vi.mock('../../components/authoring/ArtifactList', () => ({
  default: ({ onSelect, onCreate, onDelete, onDuplicate }: {
    onSelect: (a: { id: number }) => void
    onCreate: () => void
    onDelete: (id: number) => void
    onDuplicate: (id: number) => void
  }) => (
    <div data-testid="artifact-list">
      <button onClick={onCreate}>create</button>
      <button onClick={() => onSelect({ id: 1 })}>select</button>
      <button onClick={() => onDelete(1)}>delete</button>
      <button onClick={() => onDuplicate(1)}>duplicate</button>
    </div>
  ),
}))

vi.mock('../../components/authoring/ArtifactModal', () => ({
  default: ({ open, onClose }: { open: boolean; onClose: () => void }) =>
    open ? (
      <div data-testid="artifact-modal">
        <button onClick={onClose}>close-modal</button>
      </div>
    ) : null,
}))

vi.mock('../../components/authoring/ArtifactWorkspace', () => ({
  default: ({ onBack }: { onBack: () => void }) => (
    <div data-testid="artifact-workspace">
      <button onClick={onBack}>back</button>
    </div>
  ),
}))

vi.mock('../../components/authoring/import/ImportCqlDialog', () => ({
  default: ({ open }: { open: boolean }) =>
    open ? <div data-testid="import-dialog">ImportDialog</div> : null,
}))

import { useArtifacts, useArtifact, useCreateArtifact, useDeleteArtifact, useDuplicateArtifact } from '../../hooks/useAuthoring'

describe('AuthoringPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(useArtifacts).mockReturnValue({
      data: [{ id: 1, name: 'Test Artifact' }],
      isLoading: false,
      error: null,
    } as never)
    vi.mocked(useArtifact).mockReturnValue({ data: undefined } as never)
    vi.mocked(useCreateArtifact).mockReturnValue({ mutate: vi.fn() } as never)
    vi.mocked(useDeleteArtifact).mockReturnValue({ mutate: vi.fn() } as never)
    vi.mocked(useDuplicateArtifact).mockReturnValue({ mutate: vi.fn() } as never)
  })

  it('should render page title', () => {
    render(<AuthoringPage />)
    expect(screen.getByText('page.title')).toBeInTheDocument()
  })

  it('should render artifact list', () => {
    render(<AuthoringPage />)
    expect(screen.getByTestId('artifact-list')).toBeInTheDocument()
  })

  it('should open create modal', async () => {
    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('create'))

    expect(screen.getByTestId('artifact-modal')).toBeInTheDocument()
  })

  it('should close create modal', async () => {
    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('create'))
    await user.click(screen.getByText('close-modal'))

    expect(screen.queryByTestId('artifact-modal')).not.toBeInTheDocument()
  })

  it('should open delete confirmation dialog', async () => {
    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('delete'))

    expect(screen.getByText('page.deleteConfirm')).toBeInTheDocument()
  })

  it('should cancel delete dialog', async () => {
    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('delete'))
    await user.click(screen.getByText('actions.cancel'))

    await waitFor(() => {
      expect(screen.queryByText('page.deleteConfirm')).not.toBeInTheDocument()
    })
  })

  it('should show workspace when artifact is selected and loaded', async () => {
    vi.mocked(useArtifact).mockReturnValue({
      data: { id: 1, name: 'Test', elements: [] },
    } as never)

    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('select'))

    await waitFor(() => {
      expect(screen.getByTestId('artifact-workspace')).toBeInTheDocument()
    })
  })

  it('should go back from workspace to list', async () => {
    vi.mocked(useArtifact).mockReturnValue({
      data: { id: 1, name: 'Test', elements: [] },
    } as never)

    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('select'))
    await waitFor(() => expect(screen.getByTestId('artifact-workspace')).toBeInTheDocument())

    await user.click(screen.getByText('back'))

    await waitFor(() => {
      expect(screen.getByTestId('artifact-list')).toBeInTheDocument()
    })
  })

  it('should show loading skeleton when artifact is selected but not loaded', async () => {
    vi.mocked(useArtifact).mockReturnValue({ data: undefined } as never)

    const user = userEvent.setup()
    render(<AuthoringPage />)

    await user.click(screen.getByText('select'))

    // Should not show workspace (data not loaded yet)
    expect(screen.queryByTestId('artifact-workspace')).not.toBeInTheDocument()
  })
})
