import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useFhirResourceTypes, useFhirMetadata } from '../useFhirMetadata'

vi.mock('../../api', () => ({
  fhirApi: {
    getResourceTypes: vi.fn(),
    getResourceMetadata: vi.fn(),
  },
}))

import { fhirApi } from '../../api'

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

beforeEach(() => { vi.clearAllMocks() })

describe('useFhirResourceTypes', () => {
  it('should return resource types on success', async () => {
    const types = ['Patient', 'Observation', 'Condition']
    vi.mocked(fhirApi.getResourceTypes).mockResolvedValue(types)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFhirResourceTypes(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(types)
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.getResourceTypes).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFhirResourceTypes(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useFhirMetadata', () => {
  it('should fetch metadata when resourceType is provided', async () => {
    const metadata = { fields: [{ name: 'id', type: 'string' }] }
    vi.mocked(fhirApi.getResourceMetadata).mockResolvedValue(metadata)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFhirMetadata('Patient'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(metadata)
    expect(fhirApi.getResourceMetadata).toHaveBeenCalledWith('Patient')
  })

  it('should not fetch when resourceType is undefined', () => {
    vi.mocked(fhirApi.getResourceMetadata).mockResolvedValue({})
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFhirMetadata(undefined), { wrapper })

    expect(result.current.isFetching).toBe(false)
    expect(fhirApi.getResourceMetadata).not.toHaveBeenCalled()
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.getResourceMetadata).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFhirMetadata('Patient'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })

  it('should refetch when resourceType changes', async () => {
    vi.mocked(fhirApi.getResourceMetadata).mockResolvedValue({ fields: [] })
    const { wrapper } = createWrapper()

    const { result, rerender } = renderHook(
      ({ type }: { type: string | undefined }) => useFhirMetadata(type),
      { wrapper, initialProps: { type: 'Patient' as string | undefined } },
    )

    await waitFor(() => expect(result.current.isSuccess).toBe(true))

    rerender({ type: 'Observation' })

    await waitFor(() =>
      expect(fhirApi.getResourceMetadata).toHaveBeenCalledWith('Observation'),
    )
  })
})
