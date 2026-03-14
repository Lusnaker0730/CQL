import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useGenerateArtifactCql, useGenerateArtifactElm, useValidateArtifactCql, useExportArtifactZip, useFormatCql } from '../useArtifactCql'

vi.mock('../../api', () => ({
  authoringApi: {
    generateCql: vi.fn(),
    generateElm: vi.fn(),
    validateArtifactCql: vi.fn(),
    exportZip: vi.fn(),
    formatCql: vi.fn(),
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

describe('useGenerateArtifactCql', () => {
  it('should call generateCql with id', async () => {
    vi.mocked(authoringApi.generateCql).mockResolvedValue({ cql: 'library Test' })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useGenerateArtifactCql(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.generateCql).toHaveBeenCalledWith(1)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.generateCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useGenerateArtifactCql(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useGenerateArtifactElm', () => {
  it('should call generateElm with id', async () => {
    vi.mocked(authoringApi.generateElm).mockResolvedValue({ elm: '{}' })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useGenerateArtifactElm(), { wrapper })
    result.current.mutate(42)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.generateElm).toHaveBeenCalledWith(42)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.generateElm).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useGenerateArtifactElm(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useValidateArtifactCql', () => {
  it('should call validateArtifactCql with id', async () => {
    vi.mocked(authoringApi.validateArtifactCql).mockResolvedValue({ valid: true })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useValidateArtifactCql(), { wrapper })
    result.current.mutate(7)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.validateArtifactCql).toHaveBeenCalledWith(7)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.validateArtifactCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useValidateArtifactCql(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useExportArtifactZip', () => {
  it('should call exportZip with id', async () => {
    vi.mocked(authoringApi.exportZip).mockResolvedValue(new Blob(['zip']))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useExportArtifactZip(), { wrapper })
    result.current.mutate(5)

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.exportZip).toHaveBeenCalledWith(5)
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.exportZip).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useExportArtifactZip(), { wrapper })
    result.current.mutate(1)

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

describe('useFormatCql', () => {
  it('should call formatCql with cql string', async () => {
    vi.mocked(authoringApi.formatCql).mockResolvedValue({ cql: 'formatted' })
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFormatCql(), { wrapper })
    result.current.mutate('library Test')

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(authoringApi.formatCql).toHaveBeenCalledWith('library Test')
  })

  it('should handle error', async () => {
    vi.mocked(authoringApi.formatCql).mockRejectedValue(new Error('fail'))
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useFormatCql(), { wrapper })
    result.current.mutate('bad cql')

    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
