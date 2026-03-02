import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useIgPackages, useIgProfiles, useIgValueSets, useIgCodeSystems } from '../useImplementationGuide'

vi.mock('../../api', () => ({
  fhirApi: {
    getIgPackages: vi.fn(),
    browseProfiles: vi.fn(),
    browseIgValueSets: vi.fn(),
    browseIgCodeSystems: vi.fn(),
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

describe('useIgPackages', () => {
  it('should return packages on success', async () => {
    const packages = [{ id: 'tw.core', version: '0.3.0' }]
    vi.mocked(fhirApi.getIgPackages).mockResolvedValue(packages)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgPackages(), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(packages)
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.getIgPackages).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgPackages(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useIgProfiles', () => {
  it('should return profiles filtered by resourceType and search', async () => {
    const profiles = [{ url: 'http://example.com/Patient' }]
    vi.mocked(fhirApi.browseProfiles).mockResolvedValue(profiles)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgProfiles('Patient', 'tw'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(profiles)
    expect(fhirApi.browseProfiles).toHaveBeenCalledWith('Patient', 'tw')
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.browseProfiles).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgProfiles('Patient'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useIgValueSets', () => {
  it('should return value sets', async () => {
    const valueSets = [{ url: 'http://example.com/vs' }]
    vi.mocked(fhirApi.browseIgValueSets).mockResolvedValue(valueSets)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgValueSets('blood'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(valueSets)
    expect(fhirApi.browseIgValueSets).toHaveBeenCalledWith('blood')
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.browseIgValueSets).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgValueSets(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useIgCodeSystems', () => {
  it('should return code systems', async () => {
    const codeSystems = [{ url: 'http://example.com/cs' }]
    vi.mocked(fhirApi.browseIgCodeSystems).mockResolvedValue(codeSystems)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgCodeSystems('LOINC'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(codeSystems)
    expect(fhirApi.browseIgCodeSystems).toHaveBeenCalledWith('LOINC')
  })

  it('should call API without search when not provided', async () => {
    vi.mocked(fhirApi.browseIgCodeSystems).mockResolvedValue([])
    const { wrapper } = createWrapper()

    renderHook(() => useIgCodeSystems(), { wrapper })

    await waitFor(() => expect(fhirApi.browseIgCodeSystems).toHaveBeenCalledWith(undefined))
  })

  it('should handle error', async () => {
    vi.mocked(fhirApi.browseIgCodeSystems).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useIgCodeSystems(), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
