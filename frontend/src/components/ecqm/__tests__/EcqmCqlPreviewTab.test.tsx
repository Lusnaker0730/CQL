import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import EcqmCqlPreviewTab from '../EcqmCqlPreviewTab'

// Mock the React Query hooks the component depends on so we can drive states.
const generateMutate = vi.fn()
const validateMutate = vi.fn()
const publishMutate = vi.fn()

let generateState: { isPending: boolean; isError: boolean; error: unknown }
let validateState: { isPending: boolean; isError: boolean }
let publishState: { isPending: boolean; isError: boolean; isSuccess: boolean; data?: { measureDefinitionId: number } }

vi.mock('../../../hooks/useEcqm', () => ({
  useGenerateEcqmCql: () => ({ mutate: generateMutate, ...generateState }),
  useValidateEcqmCql: () => ({ mutate: validateMutate, ...validateState }),
  usePublishEcqm: () => ({ mutate: publishMutate, ...publishState }),
}))

describe('EcqmCqlPreviewTab — PAT-129 cache invalidation', () => {
  beforeEach(() => {
    generateMutate.mockReset()
    validateMutate.mockReset()
    publishMutate.mockReset()
    generateState = { isPending: false, isError: false, error: null }
    validateState = { isPending: false, isError: false }
    publishState = { isPending: false, isError: false, isSuccess: false }
  })

  it('shows the stale warning and disables Validate / Publish when artifact updated after last generate', async () => {
    // Generate succeeds with the original updatedAt — the stamp recorded by the
    // component must match exactly to be "fresh"; any divergence flips it to stale.
    generateMutate.mockImplementation((_id, opts) => {
      opts?.onSuccess?.({ cql: 'library X version 1', warnings: [] })
    })

    const { rerender } = render(
      <EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt="2026-04-25T10:00:00Z" />,
    )

    fireEvent.click(screen.getByRole('button', { name: /generate cql/i }))
    await waitFor(() => expect(screen.getByText(/library X version 1/)).toBeInTheDocument())

    // No stale alert yet — generated against the same version
    expect(screen.queryByRole('alert')).toBeNull()

    // Simulate the artifact being edited after generate by changing the prop.
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt="2026-04-25T11:00:00Z" />)

    // Stale warning appears
    expect(screen.getByRole('alert')).toBeInTheDocument()
    // Validate / Publish should be disabled until user regenerates
    expect(screen.getByRole('button', { name: /^validate$/i })).toBeDisabled()
    expect(screen.getByRole('button', { name: /publish to measure/i })).toBeDisabled()
  })

  it('clears the stale warning after a fresh regenerate against the latest updatedAt', async () => {
    let currentUpdatedAt = '2026-04-25T10:00:00Z'
    generateMutate.mockImplementation((_id, opts) => {
      opts?.onSuccess?.({ cql: 'library Y version 1', warnings: [] })
    })

    const { rerender } = render(
      <EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />,
    )
    fireEvent.click(screen.getByRole('button', { name: /generate cql/i }))
    await waitFor(() => expect(screen.getByText(/library Y version 1/)).toBeInTheDocument())

    // Edit happens
    currentUpdatedAt = '2026-04-25T11:00:00Z'
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()

    // Re-generate against the new version
    fireEvent.click(screen.getByRole('button', { name: /generate cql/i }))
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />)
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  })
})
