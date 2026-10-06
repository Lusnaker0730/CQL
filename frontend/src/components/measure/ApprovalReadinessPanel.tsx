import { useTranslation } from 'react-i18next'
import { Alert, Box, Button, Chip, CircularProgress, Paper, Stack, Typography } from '@mui/material'
import {
  CheckCircleOutlined as ReadyIcon,
  ErrorOutlined as BlockerIcon,
  WarningAmberOutlined as WarningIcon,
  Refresh as RefreshIcon,
  Groups as FourEyesIcon,
} from '@mui/icons-material'
import type { ApprovalReadiness, ApprovalReadinessItem } from '../../types'

interface ApprovalReadinessPanelProps {
  readiness: ApprovalReadiness | undefined
  isLoading: boolean
  onRefresh: () => void
}

/**
 * PAT-249 — what stands between a draft / in-review measure and its approval: blockers (the server
 * refuses submit / approve while any exist), warnings, the test case tally and the four-eyes verdict
 * for the current user. Shown above the tabs so the author sees what to fix before pressing submit.
 */
export default function ApprovalReadinessPanel({ readiness, isLoading, onRefresh }: ApprovalReadinessPanelProps) {
  const { t } = useTranslation('measures')

  if (!readiness) {
    return isLoading ? (
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', px: 2, py: 1 }} data-testid="readiness-loading">
        <CircularProgress size={14} />
        <Typography variant="caption" color="text.secondary">{t('editor.readiness.checking')}</Typography>
      </Stack>
    ) : null
  }

  const tc = readiness.testCases
  const label = (item: ApprovalReadinessItem) =>
    t(`editor.readiness.codes.${item.code}`, { count: item.count, defaultValue: item.message })

  const renderItems = (items: ApprovalReadinessItem[], severity: 'error' | 'warning') => (
    <Stack spacing={0.5}>
      {items.map((item) => (
        <Box key={item.code} data-testid={`readiness-${severity}-${item.code}`}>
          <Stack direction="row" spacing={0.75} sx={{ alignItems: 'flex-start' }}>
            {severity === 'error' ? <BlockerIcon fontSize="small" color="error" /> : <WarningIcon fontSize="small" color="warning" />}
            <Typography variant="body2">{label(item)}</Typography>
          </Stack>
          {item.items?.length > 0 && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', pl: 3.5 }}>
              {item.items.join(' · ')}
            </Typography>
          )}
        </Box>
      ))}
    </Stack>
  )

  return (
    <Paper variant="outlined" sx={{ px: 2, py: 1.5, mb: 1.5, borderColor: readiness.ready ? 'success.light' : 'error.light' }} data-testid="approval-readiness">
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        {readiness.ready
          ? <ReadyIcon color="success" fontSize="small" />
          : <BlockerIcon color="error" fontSize="small" />}
        <Typography variant="subtitle2" sx={{ flex: 1 }}>
          {readiness.ready
            ? t('editor.readiness.ready')
            : t('editor.readiness.notReady', { count: readiness.blockers.length })}
        </Typography>
        {tc && tc.total > 0 && (
          <Chip
            size="small"
            variant="outlined"
            color={tc.passed === tc.total ? 'success' : 'default'}
            label={t('editor.readiness.testCaseTally', { passed: tc.passed, total: tc.total, invalid: tc.invalid })}
            sx={{ height: 22, fontSize: '0.7rem' }}
          />
        )}
        <Button
          size="small"
          startIcon={isLoading ? <CircularProgress size={12} /> : <RefreshIcon fontSize="small" />}
          onClick={onRefresh}
          disabled={isLoading}
          sx={{ textTransform: 'none', fontSize: '0.75rem' }}
        >
          {t('editor.readiness.refresh')}
        </Button>
      </Stack>

      {readiness.blockers.length > 0 && (
        <Box sx={{ mt: 1 }}>{renderItems(readiness.blockers, 'error')}</Box>
      )}
      {readiness.warnings.length > 0 && (
        <Box sx={{ mt: 1 }}>{renderItems(readiness.warnings, 'warning')}</Box>
      )}

      {readiness.fourEyes?.enabled && (
        <Alert
          severity={readiness.fourEyes.selfApprovalBlocked ? 'info' : 'success'}
          icon={<FourEyesIcon fontSize="inherit" />}
          sx={{ mt: 1, py: 0 }}
          data-testid={readiness.fourEyes.selfApprovalBlocked ? 'four-eyes-blocked' : 'four-eyes-info'}
        >
          {readiness.fourEyes.selfApprovalBlocked
            ? t('editor.readiness.fourEyesBlocked', { author: readiness.fourEyes.author ?? '' })
            : t('editor.readiness.fourEyesInfo')}
        </Alert>
      )}
    </Paper>
  )
}
