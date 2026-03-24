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
  const connectSSE = useCallback(() => {
    const token = localStorage.getItem('token')
    if (!token) return

    const baseUrl = import.meta.env.VITE_API_URL || '/api'

    // Use the Axios client so expired JWTs are silently refreshed via
    // the response interceptor before we attempt to open the EventSource.
    api
      .post<{ ticket: string }>('/auth/sse-ticket')
      .then(({ data: { ticket } }) => {
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
          // Retry after 30s
          setTimeout(connectSSE, REFETCH_30S)
        }
      })
      .catch(() => {
        // Ticket request failed or SSE not available — rely on polling
        setTimeout(connectSSE, REFETCH_30S)
      })
  }, [queryClient])

  useEffect(() => {
    connectSSE()
    return () => {
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
