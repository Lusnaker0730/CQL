import { createContext, useState, useCallback, type ReactNode } from 'react'
import type { AlertColor } from '@mui/material'
import { NOTIFICATION_DURATION_MS } from '../constants/timing'

export interface Notification {
  id: number
  message: string
  severity: AlertColor
  duration: number
}

export interface NotificationContextType {
  notifications: Notification[]
  showNotification: (message: string, severity?: AlertColor, duration?: number) => void
  removeNotification: (id: number) => void
}

// eslint-disable-next-line react-refresh/only-export-components
export const NotificationContext = createContext<NotificationContextType | null>(null)

let nextId = 0

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([])

  const removeNotification = useCallback((id: number) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id))
  }, [])

  const showNotification = useCallback(
    (message: string, severity: AlertColor = 'info', duration = NOTIFICATION_DURATION_MS) => {
      const id = nextId++
      setNotifications((prev) => [...prev, { id, message, severity, duration }])
    },
    []
  )

  return (
    <NotificationContext.Provider value={{ notifications, showNotification, removeNotification }}>
      {children}
    </NotificationContext.Provider>
  )
}
