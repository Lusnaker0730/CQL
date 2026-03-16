import { useState } from 'react'

const TOOLBAR_BTN_BG = 'rgba(255,255,255,0.08)'
const TOOLBAR_BTN_HOVER = 'rgba(255,255,255,0.15)'
const TOOLBAR_BTN_ACTIVE = 'rgba(255,255,255,0.22)'

import {
  IconButton,
  Badge,
  Popover,
  Box,
  Typography,
  List,
  ListItemButton,
  ListItemText,
  ListItemIcon,
  Divider,
  Button,
  Stack,
} from '@mui/material'
import {
  Notifications as NotificationsIcon,
  CheckCircleOutline as ApprovedIcon,
  Cancel as RejectedIcon,
  Share as SharedIcon,
  RateReview as SubmittedIcon,
  NotificationsNone as EmptyIcon,
  DoneAll as DoneAllIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useNotifications } from '../../hooks/useNotifications'
import type { Notification } from '../../api/notificationApi'

function getNotificationIcon(type: string) {
  switch (type) {
    case 'MEASURE_APPROVED':
      return <ApprovedIcon color="success" fontSize="small" />
    case 'MEASURE_REJECTED':
      return <RejectedIcon color="error" fontSize="small" />
    case 'MEASURE_SHARED':
      return <SharedIcon color="info" fontSize="small" />
    case 'MEASURE_SUBMITTED':
      return <SubmittedIcon color="warning" fontSize="small" />
    default:
      return <NotificationsIcon fontSize="small" />
  }
}

function timeAgo(dateStr: string, t: (key: string, opts?: Record<string, unknown>) => string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return t('notifications.justNow')
  if (diffMin < 60) return t('notifications.minutesAgo', { count: diffMin })
  const diffHrs = Math.floor(diffMin / 60)
  if (diffHrs < 24) return t('notifications.hoursAgo', { count: diffHrs })
  const diffDays = Math.floor(diffHrs / 24)
  return t('notifications.daysAgo', { count: diffDays })
}

export default function NotificationBell() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const {
    notifications,
    unreadCount,
    markAsRead,
    markAllAsRead,
    deleteNotification,
  } = useNotifications()

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null)
  const open = Boolean(anchorEl)

  const handleClick = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget)
  }

  const handleClose = () => {
    setAnchorEl(null)
  }

  const handleNotificationClick = (notification: Notification) => {
    if (!notification.read) {
      markAsRead(notification.id)
    }
    if (notification.link) {
      navigate(notification.link)
      handleClose()
    }
  }

  const handleDelete = (e: React.MouseEvent, id: number) => {
    e.stopPropagation()
    deleteNotification(id)
  }

  return (
    <>
      <IconButton
        color="inherit"
        onClick={handleClick}
        aria-label={t('notifications.title')}
        title={t('notifications.title')}
        sx={{
          backgroundColor: open ? TOOLBAR_BTN_ACTIVE : TOOLBAR_BTN_BG,
          '&:hover': { backgroundColor: TOOLBAR_BTN_HOVER },
          transition: 'background-color 0.2s ease',
        }}
      >
        <Badge badgeContent={unreadCount} color="error" max={99}>
          <NotificationsIcon sx={{ fontSize: 20 }} />
        </Badge>
      </IconButton>

      <Popover
        open={open}
        anchorEl={anchorEl}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        slotProps={{
          paper: {
            sx: { width: 380, maxHeight: 480, mt: 1 },
          },
        }}
      >
        <Box sx={{ p: 2, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <Typography variant="subtitle1" fontWeight={600}>
            {t('notifications.title')}
          </Typography>
          {unreadCount > 0 && (
            <Button
              size="small"
              startIcon={<DoneAllIcon />}
              onClick={markAllAsRead}
            >
              {t('notifications.markAllRead')}
            </Button>
          )}
        </Box>
        <Divider />

        {notifications.length === 0 ? (
          <Box sx={{ p: 4, textAlign: 'center' }}>
            <EmptyIcon sx={{ fontSize: 48, color: 'text.disabled', mb: 1 }} />
            <Typography color="text.secondary">
              {t('notifications.empty')}
            </Typography>
          </Box>
        ) : (
          <List disablePadding sx={{ overflow: 'auto', maxHeight: 380 }}>
            {notifications.map((n) => (
              <ListItemButton
                key={n.id}
                onClick={() => handleNotificationClick(n)}
                sx={{
                  backgroundColor: n.read ? 'transparent' : 'action.hover',
                  '&:hover': { backgroundColor: 'action.selected' },
                }}
              >
                <ListItemIcon sx={{ minWidth: 36 }}>
                  {getNotificationIcon(n.type)}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Typography
                      variant="body2"
                      fontWeight={n.read ? 400 : 600}
                      noWrap
                    >
                      {n.title}
                    </Typography>
                  }
                  secondary={
                    <Stack>
                      {n.message && (
                        <Typography variant="caption" color="text.secondary" noWrap>
                          {n.message}
                        </Typography>
                      )}
                      <Typography variant="caption" color="text.disabled">
                        {timeAgo(n.createdAt, t)}
                      </Typography>
                    </Stack>
                  }
                />
                <IconButton
                  size="small"
                  onClick={(e) => handleDelete(e, n.id)}
                  sx={{ ml: 0.5, opacity: 0.5, '&:hover': { opacity: 1 } }}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </ListItemButton>
            ))}
          </List>
        )}
      </Popover>
    </>
  )
}
