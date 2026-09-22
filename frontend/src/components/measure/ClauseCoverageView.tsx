import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Box, Chip, FormControlLabel, Stack, Switch, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { buildSegments, describeClause, percentLabel } from '../../utils/clauseCoverage'
import { EDITOR_HEIGHT } from '../../constants/layout'
import type { ClauseCoverage } from '../../types'

interface ClauseCoverageViewProps {
  coverage: ClauseCoverage
  /** For the measure-wide view: how many test cases contributed. */
  subtitle?: string
}

/**
 * PAT-232 — the CQL with every clause coloured by whether the engine reached it (Bonnie / MADiE
 * style). Green = evaluated, red = never evaluated; hovering a clause shows its type and the
 * last value it produced. The text is the exact CQL the locators were computed from, so it is
 * rendered verbatim, not re-formatted.
 */
export default function ClauseCoverageView({ coverage, subtitle }: ClauseCoverageViewProps) {
  const { t } = useTranslation('measures')
  const [onlyUncovered, setOnlyUncovered] = useState(false)

  const clauses = useMemo(() => coverage.statements.flatMap((s) => s.clauses), [coverage])
  const segments = useMemo(() => buildSegments(coverage.cql, clauses), [coverage.cql, clauses])
  const uncoveredStatements = coverage.statements.filter((s) => s.coveredClauses < s.totalClauses)

  const statementsToShow = onlyUncovered ? uncoveredStatements : coverage.statements

  return (
    <Stack spacing={1.5} data-testid="clause-coverage">
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Chip
          color={coverage.percent >= 100 ? 'success' : coverage.percent >= 80 ? 'warning' : 'error'}
          label={t('testCases.clauseCoverage.overall', { value: percentLabel(coverage) })}
        />
        {subtitle && <Typography variant="body2" sx={{ color: 'text.secondary' }}>{subtitle}</Typography>}
        <Box sx={{ flexGrow: 1 }} />
        <FormControlLabel
          control={<Switch size="small" checked={onlyUncovered} onChange={(e) => setOnlyUncovered(e.target.checked)} />}
          label={t('testCases.clauseCoverage.onlyUncovered')}
        />
      </Stack>

      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
        <Legend color="success" label={t('testCases.clauseCoverage.legendCovered')} />
        <Legend color="error" label={t('testCases.clauseCoverage.legendUncovered')} />
      </Stack>

      {uncoveredStatements.length === 0 ? (
        <Alert severity="success">{t('testCases.clauseCoverage.allCovered')}</Alert>
      ) : (
        <Box>
          <Typography variant="subtitle2" gutterBottom>{t('testCases.clauseCoverage.byStatement')}</Typography>
          <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
            {statementsToShow.map((s) => (
              <Chip
                key={s.name}
                size="small"
                variant="outlined"
                color={s.coveredClauses === s.totalClauses ? 'success' : s.coveredClauses === 0 ? 'error' : 'warning'}
                label={`${s.function ? 'ƒ ' : ''}${s.name}: ${s.coveredClauses}/${s.totalClauses}`}
              />
            ))}
          </Stack>
        </Box>
      )}

      <Box
        component="pre"
        aria-label={t('testCases.clauseCoverage.sourceLabel')}
        sx={{
          m: 0,
          p: 1.5,
          maxHeight: EDITOR_HEIGHT,
          overflow: 'auto',
          fontFamily: 'monospace',
          fontSize: '0.8rem',
          lineHeight: 1.5,
          whiteSpace: 'pre',
          bgcolor: 'background.default',
          border: 1,
          borderColor: 'divider',
          borderRadius: 1,
        }}
      >
        {segments.map((seg, i) =>
          seg.state === 'none' ? (
            <span key={i}>{seg.text}</span>
          ) : (
            <Tooltip key={i} title={seg.clause ? describeClause(seg.clause) : ''} placement="top" enterDelay={300}>
              <Box
                component="span"
                data-coverage={seg.state}
                sx={(theme) => ({
                  bgcolor: alpha(seg.state === 'covered' ? theme.palette.success.main : theme.palette.error.main, 0.22),
                  borderBottom: `2px solid ${seg.state === 'covered' ? theme.palette.success.main : theme.palette.error.main}`,
                  cursor: 'help',
                })}
              >
                {seg.text}
              </Box>
            </Tooltip>
          ),
        )}
      </Box>
    </Stack>
  )
}

function Legend({ color, label }: { color: 'success' | 'error'; label: string }) {
  return (
    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
      <Box sx={(theme) => ({ width: 14, height: 14, borderRadius: 0.5, bgcolor: alpha(theme.palette[color].main, 0.22),
        borderBottom: `2px solid ${theme.palette[color].main}` })} />
      <Typography variant="caption">{label}</Typography>
    </Stack>
  )
}
