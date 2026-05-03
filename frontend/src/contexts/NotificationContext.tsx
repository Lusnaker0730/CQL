import { createContext, useRef, useState, useCallback, type ReactNode } from 'react'
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

export function NotificationProvider({ children }: { children: ReactNode }) {
  const [notifications, setNotifications] = useState<Notification[]>([])
  // PAT-149: per-provider counter via useRef so HMR (which retains module
  // state but resets React state) can't make two notification IDs collide
  // across hot reloads.
  const nextIdRef = useRef(0)

  const removeNotification = useCallback((id: number) => {
    setNotifications((prev) => prev.filter((n) => n.id !== id))
  }, [])

  const showNotification = useCallback(
    (message: string, severity: AlertColor = 'info', duration = NOTIFICATION_DURATION_MS) => {
      const id = nextIdRef.current++
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
