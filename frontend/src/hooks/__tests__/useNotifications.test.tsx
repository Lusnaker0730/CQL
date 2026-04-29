import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createElement } from 'react'
import { useNotifications } from '../useNotifications'

// Mock the api module — we don't want real network calls
vi.mock('../../api/client', () => ({
  api: {
    post: vi.fn(),
  },
}))

vi.mock('../../api/notificationApi', () => ({
  notificationApi: {
    getNotifications: vi.fn().mockResolvedValue([]),
    getUnreadCount: vi.fn().mockResolvedValue(0),
    markAsRead: vi.fn(),
    markAllAsRead: vi.fn(),
    deleteNotification: vi.fn(),
  },
}))

// Mock EventSource — jsdom doesn't have one
class MockEventSource {
  static instances: MockEventSource[] = []
  url: string
  readyState = 0
  onerror: ((ev: Event) => void) | null = null
  listeners = new Map<string, ((ev: MessageEvent) => void)[]>()
  closed = false

  constructor(url: string) {
    this.url = url
    MockEventSource.instances.push(this)
  }
  addEventListener(type: string, fn: (ev: MessageEvent) => void) {
    const arr = this.listeners.get(type) ?? []
    arr.push(fn)
    this.listeners.set(type, arr)
  }
  removeEventListener() {}
  close() {
    this.closed = true
  }
  static reset() {
    MockEventSource.instances = []
  }
}

// @ts-expect-error — patching globalThis for the duration of these tests
globalThis.EventSource = MockEventSource

import { api } from '../../api/client'

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

describe('useNotifications — PAT-144 SSE lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    MockEventSource.reset()
    localStorage.setItem('token', 'fake.jwt.token')
    vi.mocked(api.post).mockResolvedValue({ data: { ticket: 'ticket-1' } } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('opens an EventSource on mount when a token is present', async () => {
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    // Resolve the ticket request promise
    await vi.runAllTimersAsync()
    expect(api.post).toHaveBeenCalledWith('/auth/sse-ticket')
    expect(MockEventSource.instances.length).toBe(1)
    expect(MockEventSource.instances[0].url).toContain('ticket=ticket-1')
  })

  it('skips connecting when no auth token is present (anonymous user)', async () => {
    localStorage.removeItem('token')
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    expect(api.post).not.toHaveBeenCalled()
    expect(MockEventSource.instances.length).toBe(0)
  })

  it('closes the EventSource on unmount', async () => {
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const es = MockEventSource.instances[0]
    expect(es.closed).toBe(false)

    unmount()
    expect(es.closed).toBe(true)
  })

  it('PAT-144 regression: pending reconnect setTimeout is cleared on unmount', async () => {
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const es = MockEventSource.instances[0]

    // Trigger an SSE error → schedules reconnect 30s later
    es.onerror?.(new Event('error'))
    expect(es.closed).toBe(true)

    // Before PAT-144 the timeout would still fire after unmount and create
    // a phantom EventSource. After the fix, unmount clears the timer.
    unmount()
    await vi.runAllTimersAsync()

    // Only the initial EventSource was ever created — no phantom reconnect.
    expect(MockEventSource.instances.length).toBe(1)
  })

  it('PAT-144 regression: ticket request resolving after unmount does NOT open an EventSource', async () => {
    // Make the post hang so we control resolution timing
    let resolve!: (v: { data: { ticket: string } }) => void
    vi.mocked(api.post).mockReturnValueOnce(
      new Promise((r) => {
        resolve = r as never
      }) as never
    )

    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useNotifications(), { wrapper })

    // Unmount BEFORE the ticket promise resolves
    unmount()

    // Now resolve — the .then should refuse to create an EventSource
    resolve({ data: { ticket: 'late-ticket' } })
    await vi.runAllTimersAsync()

    expect(MockEventSource.instances.length).toBe(0)
  })

  it('reconnects after an SSE error (within active session)', async () => {
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    expect(MockEventSource.instances.length).toBe(1)

    // First EventSource errors out → schedule reconnect
    MockEventSource.instances[0].onerror?.(new Event('error'))

    // Advance past the 30s reconnect delay and resolve the new ticket request
    await vi.advanceTimersByTimeAsync(30_000)
    await vi.runAllTimersAsync()

    expect(api.post).toHaveBeenCalledTimes(2)
    expect(MockEventSource.instances.length).toBe(2)
  })

  it('returns initial query data shape', async () => {
    // Use real timers for this test so React Query's internal scheduling can
    // settle alongside waitFor's real-time polling.
    vi.useRealTimers()
    const { wrapper } = createWrapper()
    const { result } = renderHook(() => useNotifications(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.notifications).toEqual([])
    expect(result.current.unreadCount).toBe(0)
    expect(typeof result.current.markAsRead).toBe('function')
    expect(typeof result.current.markAllAsRead).toBe('function')
    expect(typeof result.current.deleteNotification).toBe('function')
  })
})
