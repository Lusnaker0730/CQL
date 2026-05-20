import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useCallback } from 'react'
import { api } from '../api/client'
import { notificationApi } from '../api/notificationApi'
import { STALE_30S, REFETCH_30S } from '../constants/queryConstants'

const NOTIFICATIONS_KEY = ['notifications']
const UNREAD_COUNT_KEY = ['notifications', 'unread-count']

// PAT-167: switched from EventSource (SSE) to WebSocket. Cloudflare was
// silently cutting SSE connections at ~100s idle even with a 25s SSE comment
// keep-alive, because Cloudflare's idle detector does not reliably count
// comment-only frames as activity. WebSocket has protocol-level ping/pong
// that Cloudflare understands natively; the backend pings every 25s and
// browsers auto-pong, so the entire `ERR_QUIC_PROTOCOL_ERROR / 524 Origin
// Timeout` family of bugs is eliminated.

function buildWsUrl(ticket: string): string {
  const baseUrl = import.meta.env.VITE_API_URL || '/api'
  // Same-origin case: `/api`. Build absolute ws(s):// from window.location.
  if (baseUrl.startsWith('/')) {
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    return `${proto}//${window.location.host}${baseUrl}/notifications/ws?ticket=${encodeURIComponent(ticket)}`
  }
  // Absolute URL configured (rare): swap http(s):// → ws(s)://
  const proto = baseUrl.startsWith('https') ? 'wss' : 'ws'
  const rest = baseUrl.replace(/^https?/, '')
  return `${proto}${rest}/notifications/ws?ticket=${encodeURIComponent(ticket)}`
}

export function useNotifications() {
  const queryClient = useQueryClient()
  const wsRef = useRef<WebSocket | null>(null)
  // PAT-144 (carried over from SSE): cancel pending reconnect on unmount, and
  // refuse to open a fresh WebSocket if the ticket request resolves after
  // the hook has already unmounted.
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isActiveRef = useRef<boolean>(true)

  const { data: notifications = [], ...notificationsQuery } = useQuery({
    queryKey: NOTIFICATIONS_KEY,
    queryFn: notificationApi.getNotifications,
    staleTime: STALE_30S,
  })

  const { data: unreadCount = 0 } = useQuery({
    queryKey: UNREAD_COUNT_KEY,
    queryFn: notificationApi.getUnreadCount,
    staleTime: STALE_30S,
  })

  const markAsReadMutation = useMutation({
    mutationFn: notificationApi.markAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
    },
  })

  const markAllAsReadMutation = useMutation({
    mutationFn: notificationApi.markAllAsRead,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
    },
  })

  const deleteMutation = useMutation({
    mutationFn: notificationApi.deleteNotification,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
      queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
    },
  })

  const scheduleReconnect = useCallback((connect: () => void) => {
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current)
    }
    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null
      if (isActiveRef.current) connect()
    }, REFETCH_30S)
  }, [])

  const connectWs = useCallback(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    // Use the Axios client so expired JWTs are silently refreshed via the
    // response interceptor before we attempt to open the WebSocket.
    api
      .post<{ ticket: string }>('/auth/sse-ticket')
      .then(({ data: { ticket } }) => {
        if (!isActiveRef.current) return

        const ws = new WebSocket(buildWsUrl(ticket))
        wsRef.current = ws

        ws.addEventListener('message', (event) => {
          let payload: { type?: string; count?: number } | null = null
          try {
            payload = JSON.parse(event.data)
          } catch {
            return
          }
          if (!payload) return
          if (payload.type === 'unread-count' && typeof payload.count === 'number') {
            queryClient.setQueryData(UNREAD_COUNT_KEY, payload.count)
          } else if (payload.type === 'notification') {
            queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
            queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
          }
        })

        const handleDrop = () => {
          if (wsRef.current === ws) {
            wsRef.current = null
          }
          scheduleReconnect(connectWs)
        }
        ws.addEventListener('close', handleDrop)
        ws.addEventListener('error', handleDrop)
      })
      .catch(() => {
        // Ticket request failed (network blip / 401 from interceptor) — rely
        // on polling and retry the whole flow.
        scheduleReconnect(connectWs)
      })
  }, [queryClient, scheduleReconnect])

  useEffect(() => {
    isActiveRef.current = true
    connectWs()
    return () => {
      isActiveRef.current = false
      if (reconnectTimerRef.current !== null) {
        clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
      wsRef.current?.close()
      wsRef.current = null
    }
  }, [connectWs])

  return {
    notifications,
    unreadCount,
    isLoading: notificationsQuery.isLoading,
    markAsRead: markAsReadMutation.mutate,
    markAllAsRead: () => markAllAsReadMutation.mutate(),
    deleteNotification: deleteMutation.mutate,
  }
}
