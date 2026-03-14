import { describe, it, expect, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import {
  useMeasuresByOwner,
  useSharedMeasures,
  useShareMeasure,
  useUnshareMeasure,
  useTransferMeasureOwnership,
  useSetMeasureAccessLevel,
  useLockMeasure,
  useUnlockMeasure,
  useSubmitForReview,
  useApproveMeasure,
  useRejectMeasure,
  useRetireMeasure,
  useValidateMeasure,
  useQuickValidateMeasure,
  useMeasureAuditTrail,
  useDashboard,
  useBatchEvaluate,
} from '../useMeasures'

vi.mock('../../api', () => ({
  measureApi: {
    getMeasuresByOwner: vi.fn(),
    getSharedMeasures: vi.fn(),
    shareMeasure: vi.fn(),
    unshareMeasure: vi.fn(),
    transferMeasureOwnership: vi.fn(),
    setMeasureAccessLevel: vi.fn(),
    lockMeasure: vi.fn(),
    unlockMeasure: vi.fn(),
    submitForReview: vi.fn(),
    approveMeasure: vi.fn(),
    rejectMeasure: vi.fn(),
    retireMeasure: vi.fn(),
    validateMeasure: vi.fn(),
    quickValidateMeasure: vi.fn(),
    getAuditTrail: vi.fn(),
    getDashboard: vi.fn(),
    batchEvaluate: vi.fn(),
  },
}))

import { measureApi } from '../../api'

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

describe('useMeasuresByOwner', () => {
  it('should fetch measures by owner when username provided', async () => {
    const measures = [{ id: 1, name: 'M1' }]
    vi.mocked(measureApi.getMeasuresByOwner).mockResolvedValue(measures)
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useMeasuresByOwner('alice'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(measures)
  })

  it('should not fetch when username is undefined', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useMeasuresByOwner(undefined), { wrapper })
    expect(result.current.isFetching).toBe(false)
  })
})

describe('useSharedMeasures', () => {
  it('should fetch shared measures', async () => {
    vi.mocked(measureApi.getSharedMeasures).mockResolvedValue([{ id: 2 }])
    const { wrapper } = createWrapper()

    const { result } = renderHook(() => useSharedMeasures('bob'), { wrapper })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(measureApi.getSharedMeasures).toHaveBeenCalledWith('bob')
  })
})

describe('useShareMeasure', () => {
  it('should share measure and invalidate', async () => {
    vi.mocked(measureApi.shareMeasure).mockResolvedValue(undefined)
    const { queryClient, wrapper } = createWrapper()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')

    const { result } = renderHook(() => useShareMeasure(), { wrapper })
    result.current.mutate({ id: 1, targetUsername: 'bob' })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(spy).toHaveBeenCalled()
  })
})

describe('invalidating mutations', () => {
  it('useUnshareMeasure should call API', async () => {
    vi.mocked(measureApi.unshareMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useUnshareMeasure(), { wrapper })
    result.current.mutate({ id: 1, targetUsername: 'bob' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useTransferMeasureOwnership should call API', async () => {
    vi.mocked(measureApi.transferMeasureOwnership).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useTransferMeasureOwnership(), { wrapper })
    result.current.mutate({ id: 1, newOwner: 'alice' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useSetMeasureAccessLevel should call API', async () => {
    vi.mocked(measureApi.setMeasureAccessLevel).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSetMeasureAccessLevel(), { wrapper })
    result.current.mutate({ id: 1, accessLevel: 'public' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useLockMeasure should call API', async () => {
    vi.mocked(measureApi.lockMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useLockMeasure(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useUnlockMeasure should call API', async () => {
    vi.mocked(measureApi.unlockMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useUnlockMeasure(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useSubmitForReview should call API', async () => {
    vi.mocked(measureApi.submitForReview).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useSubmitForReview(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useApproveMeasure should call API', async () => {
    vi.mocked(measureApi.approveMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useApproveMeasure(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useRejectMeasure should call API', async () => {
    vi.mocked(measureApi.rejectMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useRejectMeasure(), { wrapper })
    result.current.mutate({ id: 1, reason: 'needs work' })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('useRetireMeasure should call API', async () => {
    vi.mocked(measureApi.retireMeasure).mockResolvedValue(undefined)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useRetireMeasure(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })
})

describe('useValidateMeasure', () => {
  it('should call validateMeasure', async () => {
    vi.mocked(measureApi.validateMeasure).mockResolvedValue({ valid: true })
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useValidateMeasure(), { wrapper })
    result.current.mutate(1)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(measureApi.validateMeasure).toHaveBeenCalledWith(1)
  })
})

describe('useQuickValidateMeasure', () => {
  it('should call quickValidateMeasure', async () => {
    vi.mocked(measureApi.quickValidateMeasure).mockResolvedValue({ valid: true })
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useQuickValidateMeasure(), { wrapper })
    result.current.mutate(2)
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(measureApi.quickValidateMeasure).toHaveBeenCalledWith(2)
  })
})

describe('useMeasureAuditTrail', () => {
  it('should fetch audit trail when enabled and id provided', async () => {
    vi.mocked(measureApi.getAuditTrail).mockResolvedValue([{ action: 'create' }])
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useMeasureAuditTrail(1, true), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(measureApi.getAuditTrail).toHaveBeenCalledWith(1)
  })

  it('should not fetch when disabled', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useMeasureAuditTrail(1, false), { wrapper })
    expect(result.current.isFetching).toBe(false)
  })

  it('should not fetch when measureId is undefined', () => {
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useMeasureAuditTrail(undefined, true), { wrapper })
    expect(result.current.isFetching).toBe(false)
  })
})

describe('useDashboard', () => {
  it('should fetch dashboard data', async () => {
    const data = { totalMeasures: 10 }
    vi.mocked(measureApi.getDashboard).mockResolvedValue(data)
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useDashboard(), { wrapper })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toEqual(data)
  })
})

describe('useBatchEvaluate', () => {
  it('should call batchEvaluate', async () => {
    vi.mocked(measureApi.batchEvaluate).mockResolvedValue({ results: [] })
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useBatchEvaluate(), { wrapper })
    result.current.mutate({ measureIds: [1, 2] })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(measureApi.batchEvaluate).toHaveBeenCalledWith({ measureIds: [1, 2] })
  })
})
