import { Snackbar, Alert } from '@mui/material'
import { useNotification } from '../../hooks/useNotification'

export default function GlobalNotification() {
  const { notifications, removeNotification } = useNotification()

  return (
    <>
      {notifications.map((notification, index) => (
        <Snackbar
          key={notification.id}
          open
          autoHideDuration={notification.duration}
          onClose={() => removeNotification(notification.id)}
          anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
          sx={{ top: `${24 + index * 64}px !important`, zIndex: 9999 }}
        >
          <Alert
            onClose={() => removeNotification(notification.id)}
            severity={notification.severity}
            variant="filled"
            sx={{ width: '100%', minWidth: 280 }}
          >
            {notification.message}
          </Alert>
        </Snackbar>
      ))}
    </>
  )
}
