import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useTestArtifact, useDeployCdsService, useSaveAsLibrary } from '../useArtifactTesting'

vi.mock('../../api', () => ({
  authoringApi: {
    testArtifact: vi.fn(),
    deployCdsService: vi.fn(),
    saveAsLibrary: vi.fn(),
  },
}))

import { authoringApi } from '../../api'

function createWrapper() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return {
    queryClient,
    wrapper: ({ children }: { children: React.ReactNode }) =>
      createElement(QueryClientProvider, { client: queryClient }, children),
  }
}

describe('useTestArtifact', () => {
  it('should call testArtifact with correct parameters', async () => {
    vi.mocked(authoringApi.testArtifact).mockResolvedValue({ results: [] })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTestArtifact(), { wrapper })
    result.current.mutate({ id: 1, patientIds: ['p1', 'p2'], fhirServerUrl: 'http://fhir.example.com' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.testArtifact).toHaveBeenCalledWith(1, ['p1', 'p2'], 'http://fhir.example.com')
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.testArtifact).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useTestArtifact(), { wrapper })
    result.current.mutate({ id: 1, patientIds: [], fhirServerUrl: '' })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useDeployCdsService', () => {
  it('should call deployCdsService with correct parameters', async () => {
    vi.mocked(authoringApi.deployCdsService).mockResolvedValue({ serviceUrl: 'http://cds' })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useDeployCdsService(), { wrapper })
    result.current.mutate({ id: 2, serviceId: 'svc-1', hook: 'patient-view' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.deployCdsService).toHaveBeenCalledWith(2, 'svc-1', 'patient-view')
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.deployCdsService).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useDeployCdsService(), { wrapper })
    result.current.mutate({ id: 1, serviceId: 's', hook: 'h' })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useSaveAsLibrary', () => {
  it('should call saveAsLibrary with id', async () => {
    vi.mocked(authoringApi.saveAsLibrary).mockResolvedValue({ id: 'lib-1' })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSaveAsLibrary(), { wrapper })
    result.current.mutate(10)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.saveAsLibrary).toHaveBeenCalledWith(10)
  })

  it('should return data on success', async () => {
    const response = { id: 'lib-1', name: 'Test' }
    vi.mocked(authoringApi.saveAsLibrary).mockResolvedValue(response)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSaveAsLibrary(), { wrapper })
    result.current.mutate(10)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(response)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.saveAsLibrary).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSaveAsLibrary(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
