import {
  Typography,
  Paper,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Chip,
} from '@mui/material'
import WarningIcon from '@mui/icons-material/Warning'
import ErrorIcon from '@mui/icons-material/Error'
import { useTranslation } from 'react-i18next'
import type { ThresholdAlert } from '../../types'

interface ThresholdAlertPanelProps {
  alerts: ThresholdAlert[]
}

export default function ThresholdAlertPanel({ alerts }: ThresholdAlertPanelProps) {
  const { t } = useTranslation('measures')

  if (alerts.length === 0) {
    return (
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>
          {t('dashboard.alerts')}
        </Typography>
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          {t('dashboard.noAlerts')}
        </Typography>
      </Paper>
    )
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {t('dashboard.alerts')} ({alerts.length})
      </Typography>
      <List dense>
        {alerts.map((alert, i) => (
          <ListItem key={i} sx={{ px: 0 }}>
            <ListItemIcon sx={{ minWidth: 36 }}>
              {alert.severity === 'critical' ? (
                <ErrorIcon color="error" fontSize="small" />
              ) : (
                <WarningIcon color="warning" fontSize="small" />
              )}
            </ListItemIcon>
            <ListItemText
              primary={alert.measureName}
              secondary={`${t('dashboard.score')}: ${alert.actualScore?.toFixed(1)}% ${alert.comparisonOperator} ${alert.thresholdValue}%`}
              primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
              secondaryTypographyProps={{ variant: 'caption' }}
            />
            <Chip
              label={t(`dashboard.thresholdType.${alert.thresholdType}`, { defaultValue: alert.thresholdType })}
              size="small"
              color={alert.severity === 'critical' ? 'error' : 'warning'}
            />
          </ListItem>
        ))}
      </List>
    </Paper>
  )
}
