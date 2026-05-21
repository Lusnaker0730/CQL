import { useEffect } from 'react'
import { Alert, Box, Slide } from '@mui/material'
import { useNotification } from '../../hooks/useNotification'
import type { Notification } from '../../contexts/NotificationContext'

/**
 * Global stack of toast notifications, positioned at the top-right of the
 * viewport. The stack uses a single fixed-position flex container so the items
 * reflow naturally when any item is removed — the previous implementation
 * positioned each Snackbar individually with `top: ${24 + index * 64}px`,
 * which made middle-of-stack dismissals leave a gap that the lower
 * notifications then jumped through.
 */
export default function GlobalNotification() {
  const { notifications, removeNotification } = useNotification()

  return (
    <Box
      sx={{
        position: 'fixed',
        top: 24,
        right: 24,
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: 1,
        maxWidth: 'calc(100vw - 48px)',
        // The container itself shouldn't trap pointer events between
        // notifications — only the rendered alerts should be clickable.
        pointerEvents: 'none',
      }}
    >
      {notifications.map((notification) => (
        <Box key={notification.id} sx={{ pointerEvents: 'auto' }}>
          <NotificationItem notification={notification} onClose={removeNotification} />
        </Box>
      ))}
    </Box>
  )
}

function NotificationItem({
  notification,
  onClose,
}: {
  notification: Notification
  onClose: (id: number) => void
}) {
  useEffect(() => {
    if (!notification.duration) return
    const timer = setTimeout(() => onClose(notification.id), notification.duration)
    return () => clearTimeout(timer)
  }, [notification.id, notification.duration, onClose])

  return (
    <Slide direction="left" in mountOnEnter unmountOnExit>
      <Alert
        onClose={() => onClose(notification.id)}
        severity={notification.severity}
        variant="filled"
        sx={{ minWidth: 280 }}
      >
        {notification.message}
      </Alert>
    </Slide>
  )
}
