import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '../../../test/test-utils'
import EcqmCqlPreviewTab from '../EcqmCqlPreviewTab'
import { AxiosError, AxiosHeaders } from 'axios'

// i18next isn't initialized in the shared test-utils wrapper, so `useTranslation`
// would return raw keys; mock to make queries stable.
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
    generateMutate.mockImplementation((_id, opts) => {
      opts?.onSuccess?.({ cql: 'library X version 1', warnings: [] })
    })

    const { rerender } = render(
      <EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt="2026-04-25T10:00:00Z" />,
    )

    // Find the Generate button — its label is the i18n key 'cqlPreview.generate'
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.generate' }))
    await waitFor(() => expect(screen.getByText(/library X version 1/)).toBeInTheDocument())

    // No stale alert yet — generated against the same version
    expect(screen.queryByRole('alert')).toBeNull()

    // Simulate the artifact being edited after generate by changing the prop.
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt="2026-04-25T11:00:00Z" />)

    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'cqlPreview.validate' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'cqlPreview.publishToMeasure' })).toBeDisabled()
  })

  it('clears the stale warning after a fresh regenerate against the latest updatedAt', async () => {
    let currentUpdatedAt = '2026-04-25T10:00:00Z'
    generateMutate.mockImplementation((_id, opts) => {
      opts?.onSuccess?.({ cql: 'library Y version 1', warnings: [] })
    })

    const { rerender } = render(
      <EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />,
    )
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.generate' }))
    await waitFor(() => expect(screen.getByText(/library Y version 1/)).toBeInTheDocument())

    currentUpdatedAt = '2026-04-25T11:00:00Z'
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.generate' }))
    rerender(<EcqmCqlPreviewTab artifactId={1} artifactUpdatedAt={currentUpdatedAt} />)
    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull())
  })
})

// PAT-238 — a 409 "Publish Conflict" (measure edited on the measure page since the last publish)
// opens a confirm; overwriting re-publishes with force, cancelling publishes nothing.
describe('EcqmCqlPreviewTab — PAT-238 publish conflict', () => {
  beforeEach(() => {
    generateMutate.mockReset()
    publishMutate.mockReset()
    generateState = { isPending: false, isError: false, error: null }
    validateState = { isPending: false, isError: false }
    publishState = { isPending: false, isError: false, isSuccess: false }
    generateMutate.mockImplementation((_id, opts) => { opts?.onSuccess?.({ cql: 'library X version 1', warnings: [] }) })
  })

  function conflict() {
    return new AxiosError('conflict', '409', undefined, undefined, {
      status: 409, statusText: 'Conflict', headers: {}, config: { headers: new AxiosHeaders() },
      data: { error: 'Publish Conflict', message: 'edited' },
    })
  }

  it('asks before overwriting and re-publishes with force on confirm', async () => {
    publishMutate.mockImplementationOnce((_args, opts) => opts?.onError?.(conflict()))
    render(<EcqmCqlPreviewTab artifactId={7} artifactUpdatedAt="2026-04-25T10:00:00Z" />)
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.generate' }))
    await waitFor(() => expect(screen.getByText(/library X version 1/)).toBeInTheDocument())

    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.publishToMeasure' }))
    expect(publishMutate).toHaveBeenLastCalledWith({ id: 7, force: false }, expect.anything())
    expect(await screen.findByText('publishConflict.title')).toBeInTheDocument()

    fireEvent.click(screen.getByRole('button', { name: 'publishConflict.overwrite' }))
    expect(publishMutate).toHaveBeenLastCalledWith({ id: 7, force: true }, expect.anything())
    expect(publishMutate).toHaveBeenCalledTimes(2)
  })

  it('cancel publishes nothing more; any other error does not open the confirm', async () => {
    publishMutate.mockImplementationOnce((_args, opts) => opts?.onError?.(conflict()))
    render(<EcqmCqlPreviewTab artifactId={7} artifactUpdatedAt="2026-04-25T10:00:00Z" />)
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.generate' }))
    await waitFor(() => expect(screen.getByText(/library X version 1/)).toBeInTheDocument())
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.publishToMeasure' }))
    fireEvent.click(await screen.findByRole('button', { name: 'common:actions.cancel' }))
    await waitFor(() => expect(screen.queryByText('publishConflict.title')).not.toBeInTheDocument())
    expect(publishMutate).toHaveBeenCalledTimes(1)

    publishMutate.mockImplementationOnce((_args, opts) => opts?.onError?.(new Error('boom')))
    fireEvent.click(screen.getByRole('button', { name: 'cqlPreview.publishToMeasure' }))
    expect(screen.queryByText('publishConflict.title')).not.toBeInTheDocument()
  })
})
