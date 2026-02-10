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
import type { TestCaseRunResult } from '../../types'

interface TestCaseResultProps {
  result: TestCaseRunResult
}

const STATUS_CONFIG = {
  pass: { icon: <PassIcon sx={{ fontSize: 18 }} />, color: 'success' as const, label: 'Pass' },
  fail: { icon: <FailIcon sx={{ fontSize: 18 }} />, color: 'error' as const, label: 'Fail' },
  error: { icon: <ErrorIcon sx={{ fontSize: 18 }} />, color: 'warning' as const, label: 'Error' },
}

export default function TestCaseResult({ result }: TestCaseResultProps) {
  const config = STATUS_CONFIG[result.status as keyof typeof STATUS_CONFIG] || STATUS_CONFIG.error

  return (
    <Box sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={1}>
        <Stack direction="row" spacing={1} alignItems="center">
          <Chip
            icon={config.icon}
            label={config.label}
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
            {result.executionTimeMs}ms
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
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }}>Population</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">Expected</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">Actual</TableCell>
              <TableCell scope="col" sx={{ py: 0.5, fontWeight: 600, fontSize: '0.75rem' }} align="center">Result</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {result.comparisons.map((comp) => (
              <TableRow key={comp.populationType}>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }}>
                  {comp.populationType}
                </TableCell>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }} align="center">
                  {comp.expected == null ? '-' : comp.expected ? 'Yes' : 'No'}
                </TableCell>
                <TableCell sx={{ py: 0.5, fontSize: '0.8rem' }} align="center">
                  {comp.actual == null ? '-' : comp.actual ? 'Yes' : 'No'}
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
