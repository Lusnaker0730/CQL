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

// PAT-167: jsdom doesn't ship a WebSocket implementation. Replace with a
// minimal stub that records construction args, lets tests inject messages,
// and tracks close state.
class MockWebSocket {
  static instances: MockWebSocket[] = []
  url: string
  readyState = 0
  closed = false
  listeners = new Map<string, ((ev: MessageEvent | Event) => void)[]>()

  constructor(url: string) {
    this.url = url
    MockWebSocket.instances.push(this)
  }
  addEventListener(type: string, fn: (ev: MessageEvent | Event) => void) {
    const arr = this.listeners.get(type) ?? []
    arr.push(fn)
    this.listeners.set(type, arr)
  }
  removeEventListener() {}
  close() {
    this.closed = true
    this.fire('close', new Event('close'))
  }
  fire(type: string, ev: MessageEvent | Event) {
    for (const fn of this.listeners.get(type) ?? []) fn(ev)
  }
  static reset() {
    MockWebSocket.instances = []
  }
}

// @ts-expect-error — patching globalThis for the duration of these tests
globalThis.WebSocket = MockWebSocket

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

describe('useNotifications — PAT-167 WebSocket lifecycle', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    MockWebSocket.reset()
    localStorage.setItem('token', 'fake.jwt.token')
    vi.mocked(api.post).mockResolvedValue({ data: { ticket: 'ticket-1' } } as never)
  })

  afterEach(() => {
    vi.useRealTimers()
    localStorage.clear()
    vi.clearAllMocks()
  })

  it('opens a WebSocket on mount when a token is present', async () => {
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    // Resolve the ticket request promise
    await vi.runAllTimersAsync()
    expect(api.post).toHaveBeenCalledWith('/auth/sse-ticket')
    expect(MockWebSocket.instances.length).toBe(1)
    expect(MockWebSocket.instances[0].url).toContain('/api/notifications/ws')
    expect(MockWebSocket.instances[0].url).toContain('ticket=ticket-1')
    expect(MockWebSocket.instances[0].url.startsWith('ws://') || MockWebSocket.instances[0].url.startsWith('wss://')).toBe(true)
  })

  it('skips connecting when no auth token is present (anonymous user)', async () => {
    localStorage.removeItem('token')
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    expect(api.post).not.toHaveBeenCalled()
    expect(MockWebSocket.instances.length).toBe(0)
  })

  it('closes the WebSocket on unmount', async () => {
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const ws = MockWebSocket.instances[0]
    expect(ws.closed).toBe(false)

    unmount()
    expect(ws.closed).toBe(true)
  })

  it('PAT-144 carry-over: pending reconnect setTimeout is cleared on unmount', async () => {
    const { wrapper } = createWrapper()
    const { unmount } = renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const ws = MockWebSocket.instances[0]

    // Simulate a transport drop → schedules reconnect 30s later
    ws.fire('error', new Event('error'))

    // Before the cleanup, the timeout would still fire after unmount and
    // create a phantom WebSocket. After unmount the timer is cleared.
    unmount()
    await vi.runAllTimersAsync()

    // Only the initial WebSocket was ever created — no phantom reconnect.
    // (Two instances would mean the second WS got created after unmount.)
    expect(MockWebSocket.instances.length).toBe(1)
  })

  it('PAT-144 carry-over: ticket request resolving after unmount does NOT open a WebSocket', async () => {
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

    // Now resolve — the .then should refuse to create a WebSocket
    resolve({ data: { ticket: 'late-ticket' } })
    await vi.runAllTimersAsync()

    expect(MockWebSocket.instances.length).toBe(0)
  })

  it('reconnects after a WebSocket close/error (within active session)', async () => {
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    expect(MockWebSocket.instances.length).toBe(1)

    // First WS errors out → schedule reconnect
    MockWebSocket.instances[0].fire('error', new Event('error'))

    // Advance past the 30s reconnect delay and resolve the new ticket request
    await vi.advanceTimersByTimeAsync(30_000)
    await vi.runAllTimersAsync()

    expect(api.post).toHaveBeenCalledTimes(2)
    expect(MockWebSocket.instances.length).toBe(2)
  })

  it('applies unread-count messages by setting the React Query cache', async () => {
    const { wrapper, queryClient } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const ws = MockWebSocket.instances[0]

    ws.fire('message', new MessageEvent('message', {
      data: JSON.stringify({ type: 'unread-count', count: 7 }),
    }))

    expect(queryClient.getQueryData(['notifications', 'unread-count'])).toBe(7)
  })

  it('invalidates query cache when a notification message arrives', async () => {
    const { wrapper, queryClient } = createWrapper()
    const spy = vi.spyOn(queryClient, 'invalidateQueries')
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const ws = MockWebSocket.instances[0]

    ws.fire('message', new MessageEvent('message', {
      data: JSON.stringify({
        type: 'notification',
        id: 42,
        notificationType: 'MEASURE_SUBMITTED',
        title: 't',
        message: 'm',
        link: '/measures/1',
        createdAt: '2026-05-21T00:00:00',
      }),
    }))

    expect(spy).toHaveBeenCalledWith({ queryKey: ['notifications'] })
    expect(spy).toHaveBeenCalledWith({ queryKey: ['notifications', 'unread-count'] })
  })

  it('ignores malformed (non-JSON) WebSocket messages without crashing', async () => {
    const { wrapper } = createWrapper()
    renderHook(() => useNotifications(), { wrapper })

    await vi.runAllTimersAsync()
    const ws = MockWebSocket.instances[0]

    expect(() => {
      ws.fire('message', new MessageEvent('message', { data: 'not json {' }))
    }).not.toThrow()
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
