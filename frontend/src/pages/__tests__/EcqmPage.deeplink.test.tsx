import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '../../test/test-utils'
import EcqmPage from '../EcqmPage'

// PAT-238 — /ecqm?artifact=<id> (the measure page's "Open in builder") opens that artifact.
const useEcqmArtifact = vi.fn()
vi.mock('../../hooks/useEcqm', () => ({
  useEcqmArtifacts: () => ({ data: [] }),
  useEcqmArtifact: (id: number | undefined) => useEcqmArtifact(id),
  useCreateEcqmArtifact: () => ({ mutate: vi.fn() }),
  useDeleteEcqmArtifact: () => ({ mutate: vi.fn() }),
  useDuplicateEcqmArtifact: () => ({ mutate: vi.fn() }),
}))
vi.mock('../../components/ecqm/EcqmArtifactList', () => ({ default: () => <div data-testid="artifact-list" /> }))
vi.mock('../../components/ecqm/EcqmArtifactModal', () => ({ default: () => null }))
vi.mock('../../components/ecqm/EcqmArtifactWorkspace', () => ({
  default: ({ artifact }: { artifact: { id: number } }) => <div data-testid="workspace">artifact {artifact.id}</div>,
}))

describe('EcqmPage deep link', () => {
  it('opens the artifact named in ?artifact=', () => {
    useEcqmArtifact.mockImplementation((id?: number) => ({ data: id ? { id } : undefined, isLoading: false }))
    render(<EcqmPage />, { route: '/ecqm?artifact=42' })
    expect(screen.getByTestId('workspace')).toHaveTextContent('artifact 42')
  })

  it('without the parameter shows the list', () => {
    useEcqmArtifact.mockImplementation(() => ({ data: undefined, isLoading: false }))
    render(<EcqmPage />, { route: '/ecqm' })
    expect(screen.getByTestId('artifact-list')).toBeInTheDocument()
  })
})
