import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useEffect, useRef, useCallback } from 'react'
import { notificationApi } from '../api/notificationApi'

const NOTIFICATIONS_KEY = ['notifications']
const UNREAD_COUNT_KEY = ['notifications', 'unread-count']

export function useNotifications() {
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)

  const { data: notifications = [], ...notificationsQuery } = useQuery({
    queryKey: NOTIFICATIONS_KEY,
    queryFn: notificationApi.getNotifications,
    staleTime: 30_000,
  })

  const { data: unreadCount = 0 } = useQuery({
    queryKey: UNREAD_COUNT_KEY,
    queryFn: notificationApi.getUnreadCount,
    staleTime: 30_000,
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
    // EventSource doesn't support custom headers, so pass JWT as query parameter
    const url = `${baseUrl}/notifications/subscribe?token=${encodeURIComponent(token)}`

    try {
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
        setTimeout(connectSSE, 30_000)
      }
    } catch {
      // SSE not available, rely on polling
    }
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
