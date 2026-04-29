import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useCallback } from 'react'
import { api } from '../api/client'
import { notificationApi } from '../api/notificationApi'
import { STALE_30S, REFETCH_30S } from '../constants/queryConstants'

const NOTIFICATIONS_KEY = ['notifications']
const UNREAD_COUNT_KEY = ['notifications', 'unread-count']

export function useNotifications() {
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)
  // PAT-144: track pending reconnect timer + mount state so cleanup can cancel
  // a setTimeout fired from the previous SSE error handler, and so a slow
  // ticket request that resolves after unmount doesn't create an orphan
  // EventSource that never gets closed.
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

  // SSE subscription for real-time updates
  const scheduleReconnect = useCallback((connect: () => void) => {
    if (reconnectTimerRef.current !== null) {
      clearTimeout(reconnectTimerRef.current)
    }
    reconnectTimerRef.current = setTimeout(() => {
      reconnectTimerRef.current = null
      if (isActiveRef.current) connect()
    }, REFETCH_30S)
  }, [])

  const connectSSE = useCallback(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    const baseUrl = import.meta.env.VITE_API_URL || '/api'

    // Use the Axios client so expired JWTs are silently refreshed via
    // the response interceptor before we attempt to open the EventSource.
    api
      .post<{ ticket: string }>('/auth/sse-ticket')
      .then(({ data: { ticket } }) => {
        // PAT-144: hook may have been unmounted while the ticket request was
        // in flight — refuse to create an EventSource that nothing will close.
        if (!isActiveRef.current) return

        const url = `${baseUrl}/notifications/subscribe?ticket=${encodeURIComponent(ticket)}`
        const es = new EventSource(url)
        eventSourceRef.current = es

        es.addEventListener('notification', () => {
          queryClient.invalidateQueries({ queryKey: NOTIFICATIONS_KEY })
          queryClient.invalidateQueries({ queryKey: UNREAD_COUNT_KEY })
        })

        es.addEventListener('unread-count', (event) => {
          const count = parseInt(event.data, 10)
          if (!isNaN(count)) {
            queryClient.setQueryData(UNREAD_COUNT_KEY, count)
          }
        })

        es.onerror = () => {
          es.close()
          eventSourceRef.current = null
          scheduleReconnect(connectSSE)
        }
      })
      .catch(() => {
        // Ticket request failed or SSE not available — rely on polling.
        scheduleReconnect(connectSSE)
      })
  }, [queryClient, scheduleReconnect])

  useEffect(() => {
    isActiveRef.current = true
    connectSSE()
    return () => {
      isActiveRef.current = false
      if (reconnectTimerRef.current !== null) {
        clearTimeout(reconnectTimerRef.current)
        reconnectTimerRef.current = null
      }
      eventSourceRef.current?.close()
      eventSourceRef.current = null
    }
  }, [connectSSE])

  return {
    notifications,
    unreadCount,
    isLoading: notificationsQuery.isLoading,
    markAsRead: markAsReadMutation.mutate,
    markAllAsRead: () => markAllAsReadMutation.mutate(),
    deleteNotification: deleteMutation.mutate,
  }
}
