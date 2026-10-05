import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Box, Button, Chip, Collapse, Stack, Tooltip, Typography } from '@mui/material'
import {
  VerifiedUser as ValidIcon,
  ReportProblem as InvalidIcon,
  HourglassEmpty as PendingIcon,
  ErrorOutlined as ErrorIcon,
  HelpOutlined as UnknownIcon,
} from '@mui/icons-material'
import type { TestCase } from '../../types'

interface Props {
  testCase: TestCase
  /** Opens the issue list without a click (the editor); the list rows start collapsed. */
  defaultExpanded?: boolean
  /** Chip with tooltip only — no issue list (the list row header). */
  compact?: boolean
  onRevalidate?: () => void
  revalidating?: boolean
}

type Status = 'valid' | 'invalid' | 'pending' | 'error' | 'none'

const CHIP: Record<Status, { color: 'success' | 'error' | 'default' | 'warning'; icon: React.ReactElement }> = {
  valid: { color: 'success', icon: <ValidIcon sx={{ fontSize: 14 }} /> },
  invalid: { color: 'error', icon: <InvalidIcon sx={{ fontSize: 14 }} /> },
  pending: { color: 'default', icon: <PendingIcon sx={{ fontSize: 14 }} /> },
  error: { color: 'warning', icon: <ErrorIcon sx={{ fontSize: 14 }} /> },
  none: { color: 'default', icon: <UnknownIcon sx={{ fontSize: 14 }} /> },
}

/**
 * PAT-245 — the FHIR validation status of a test case's patient bundle: a chip (Valid / Invalid /
 * Pending / Error / Not validated) that opens the error issues, with an optional re-validate button.
 */
export default function TestCaseValidationBadge({ testCase, defaultExpanded = false, compact = false, onRevalidate, revalidating }: Props) {
  const { t } = useTranslation('measures')
  const [open, setOpen] = useState(defaultExpanded)
  const status = (testCase.validationStatus as Status | undefined) ?? 'none'
  const validation = testCase.validation
  const issues = validation?.issues ?? []
  const chip = CHIP[status] ?? CHIP.none

  const summary = validation && status !== 'pending' && status !== 'none'
    ? t('testCases.validation.summary', {
        resources: validation.totalResources ?? 0,
        invalid: validation.invalidResources ?? 0,
        errors: validation.errorCount ?? 0,
        warnings: validation.warningCount ?? 0,
      })
    : t(`testCases.validation.hint.${status}`)

  return (
    <Box data-testid={`validation-badge-${testCase.id ?? 'new'}`}>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
        <Tooltip title={summary}>
          <Chip
            size="small"
            color={chip.color}
            variant={status === 'none' || status === 'pending' ? 'outlined' : 'filled'}
            icon={chip.icon}
            label={t(`testCases.validation.status.${status}`)}
            onClick={!compact && (issues.length > 0 || validation?.message) ? () => setOpen((o) => !o) : undefined}
            sx={{ height: 20, fontSize: '0.65rem' }}
            data-testid="validation-status-chip"
          />
        </Tooltip>
        {onRevalidate && (
          <Button size="small" onClick={onRevalidate} disabled={revalidating || status === 'pending'} sx={{ minWidth: 0, py: 0 }}>
            {revalidating ? t('testCases.validation.validating') : t('testCases.validation.revalidate')}
          </Button>
        )}
      </Stack>
      <Collapse in={!compact && open && (issues.length > 0 || !!validation?.message)} unmountOnExit>
        <Box sx={{ mt: 0.5, pl: 1, borderLeft: '2px solid', borderColor: 'divider' }} data-testid="validation-issues">
          {validation?.message && (
            <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>{validation.message}</Typography>
          )}
          {issues.map((issue, i) => (
            <Typography key={i} variant="caption" sx={{ display: 'block', fontFamily: 'monospace', fontSize: '0.7rem' }}>
              {issue.resourceType}{issue.resourceId ? `/${issue.resourceId}` : ''}
              {issue.location ? ` ${issue.location}` : ''}: {issue.message}
            </Typography>
          ))}
          {(validation?.errorCount ?? 0) > issues.length && (
            <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
              {t('testCases.validation.moreIssues', { count: (validation?.errorCount ?? 0) - issues.length })}
            </Typography>
          )}
        </Box>
      </Collapse>
    </Box>
  )
}
