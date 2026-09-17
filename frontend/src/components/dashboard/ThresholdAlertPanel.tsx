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
import { formatScoreValue, formatThresholdValue } from '../../utils/dashboardFormat'

interface ThresholdAlertPanelProps {
  alerts: ThresholdAlert[]
}

export default function ThresholdAlertPanel({ alerts }: ThresholdAlertPanelProps) {
  const { t } = useTranslation('measures')
  const { t: tCommon } = useTranslation('common')

  if (alerts.length === 0) {
    return (
      <Paper variant="outlined" sx={{ p: 2 }}>
        <Typography variant="subtitle2" gutterBottom>
          {t('dashboard.alerts')}
        </Typography>
        <Typography
          variant="body2"
          sx={{
            color: "text.secondary",
            py: 2,
            textAlign: 'center'
          }}>
          {t('dashboard.noAlerts')}
        </Typography>
      </Paper>
    );
  }

  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        {t('dashboard.alerts')} ({alerts.length})
      </Typography>
      <List dense>
        {alerts.map((alert) => {
          // PAT-151: route both numbers through the dashboard formatter so
          // continuous-variable thresholds (HbA1c mmol/L) and cohort counts
          // don't get rendered with a stray '%'. Fallback to the common
          // "N/A" label when actualScore is null instead of "undefined.0%".
          const naLabel = tCommon('notAvailable')
          const fmtOpts = { scoringType: alert.scoringType, unit: alert.unit, naLabel }
          const score = formatScoreValue(alert.actualScore, fmtOpts)
          const threshold = formatThresholdValue(alert.thresholdValue, fmtOpts)
          return (
            <ListItem
              // PAT-151: stable key from the alert tuple so list re-renders
              // don't reuse a wrong row when alerts are filtered/reordered.
              key={`${alert.measureId}-${alert.thresholdType}`}
              sx={{ px: 0 }}
            >
              <ListItemIcon sx={{ minWidth: 36 }}>
                {alert.severity === 'critical' ? (
                  <ErrorIcon color="error" fontSize="small" />
                ) : (
                  <WarningIcon color="warning" fontSize="small" />
                )}
              </ListItemIcon>
              <ListItemText
                primary={alert.measureName}
                secondary={`${t('dashboard.score')}: ${score} ${alert.comparisonOperator} ${threshold}`}
                slotProps={{
                  primary: { variant: 'body2', sx: { fontWeight: 600 } },
                  secondary: { variant: 'caption' }
                }} />
              <Chip
                label={t(`dashboard.thresholdType.${alert.thresholdType}`, { defaultValue: alert.thresholdType })}
                size="small"
                color={alert.severity === 'critical' ? 'error' : 'warning'}
              />
            </ListItem>
          );
        })}
      </List>
    </Paper>
  );
}
