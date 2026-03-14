import { api } from './client'

export interface Notification {
  id: number
  recipient: string
  type: string
  title: string
  message: string
  link: string
  read: boolean
  createdAt: string
  readAt: string | null
}

export const notificationApi = {
  getNotifications: async (): Promise<Notification[]> => {
    const { data } = await api.get('/notifications')
    return data
  },

  getUnreadCount: async (): Promise<number> => {
    const { data } = await api.get('/notifications/unread-count')
    return data.count
  },

  markAsRead: async (id: number): Promise<Notification> => {
    const { data } = await api.post(`/notifications/${id}/read`)
    return data
  },

  markAllAsRead: async (): Promise<number> => {
    const { data } = await api.post('/notifications/read-all')
    return data.updated
  },

  deleteNotification: async (id: number): Promise<void> => {
    await api.delete(`/notifications/${id}`)
  },
}
