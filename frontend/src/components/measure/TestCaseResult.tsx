import {
  Box,
  Typography,
  Stack,
  Chip,
  Table,
  TableBody,
  TableRow,
  TableCell,
  TableHead,
} from '@mui/material'
import {
  CheckCircle as PassIcon,
  Cancel as FailIcon,
  Error as ErrorIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { TestCaseRunResult } from '../../types'

interface TestCaseResultProps {
  result: TestCaseRunResult
}

const STATUS_CONFIG = {
  pass: { icon: <PassIcon sx={{ fontSize: 18 }} />, color: 'success' as const },
  fail: { icon: <FailIcon sx={{ fontSize: 18 }} />, color: 'error' as const },
  error: { icon: <ErrorIcon sx={{ fontSize: 18 }} />, color: 'warning' as const },
}

const STATUS_LABEL_KEYS: Record<string, string> = {
  pass: 'testCaseResult.pass',
  fail: 'testCaseResult.fail',
  error: 'testCaseResult.error',
}

export default function TestCaseResult({ result }: TestCaseResultProps) {
  const { t } = useTranslation('measures')
  const statusKey = (result.status as keyof typeof STATUS_CONFIG) || 'error'
  const config = STATUS_CONFIG[statusKey] || STATUS_CONFIG.error
  const statusLabel = t(STATUS_LABEL_KEYS[statusKey] || STATUS_LABEL_KEYS.error)

  return (
    <Box sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            icon={config.icon}
            label={statusLabel}
            size="small"
            color={config.color}
            variant="outlined"
            sx={{ fontWeight: 600 }}
          />
          <Typography variant="body2" fontWeight={500}>
            {result.testCaseTitle}
          </Typography>
        </Stack>
        {result.executionTimeMs != null && (
          <Typography variant="caption" color="text.secondary">
            {result.executionTimeMs}{t('testCaseResult.ms')}
          </Typography>
        )}
      </Stack>

      {result.errorMessage && (
        <Typography variant="body2" color="error.main" sx={{ mb: 1, fontSize: '0.8rem' }}>
          {result.errorMessage}
        </Typography>
      )}

      {result.comparisons && result.comparisons.length > 0 && (
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }}>{t('testCaseResult.tableHeaders.population')}</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">{t('testCaseResult.tableHeaders.expected')}</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">{t('testCaseResult.tableHeaders.actual')}</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">{t('testCaseResult.tableHeaders.result')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {result.comparisons.map((comp) => (
              <TableRow key={comp.populationType}>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }}>
                  {comp.populationType}
                </TableCell>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }} align="center">
                  {comp.expected == null ? t('testCaseResult.na') : comp.expected ? t('testCaseResult.yes') : t('testCaseResult.no')}
                </TableCell>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }} align="center">
                  {comp.actual == null ? t('testCaseResult.na') : comp.actual ? t('testCaseResult.yes') : t('testCaseResult.no')}
                </TableCell>
                <TableCell sx={{ py: 0.5 }} align="center">
                  {comp.match ? (
                    <PassIcon sx={{ fontSize: 16, color: 'success.main' }} />
                  ) : (
                    <FailIcon sx={{ fontSize: 16, color: 'error.main' }} />
                  )}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
    </Box>
  )
}
