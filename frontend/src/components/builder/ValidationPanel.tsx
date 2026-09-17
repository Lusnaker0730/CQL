import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import { Alert, Box, Stack, Typography, Chip } from '@mui/material'
import {
  CheckCircle as CheckIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
} from '@mui/icons-material'
import type { CqlStructure } from '../../hooks/useCqlStructure'
import { extractCqlName } from '../../utils/cqlNames'

interface ValidationPanelProps {
  structure: CqlStructure
  parseError: string | null
}

export interface ValidationIssue {
  severity: 'error' | 'warning' | 'info'
  message: string
  element?: string
}

type TFunc = (key: string, opts?: Record<string, unknown>) => string

/** Run basic static analysis on the parsed CQL structure */
function analyzeStructure(structure: CqlStructure, t: TFunc): ValidationIssue[] {
  const issues: ValidationIssue[] = []
  const allExprNames = new Set(structure.expressions.map((e) => e.name))
  const paramNames = new Set(structure.parameters.map((p) => extractCqlName(p)).filter(Boolean))

  // Check for type-inferred "null" expressions (likely unfinished)
  for (const expr of structure.expressions) {
    if (expr.resultType === 'System.Any') {
      issues.push({
        severity: 'info',
        message: t('validation.systemAnyType', { name: expr.name }),
        element: expr.name,
      })
    }
  }

  // Check required CDS patterns
  const hasRecommendation = structure.expressions.some((e) =>
    e.name.toLowerCase().startsWith('recommendation') || e.name.toLowerCase().startsWith('card')
  )
  const hasInclusion = allExprNames.has('MeetsInclusionCriteria')
  const hasExclusion = allExprNames.has('MeetsExclusionCriteria')
  const hasInPopulation = allExprNames.has('InPopulation')

  if (hasRecommendation && !hasInPopulation) {
    issues.push({
      severity: 'warning',
      message: t('validation.missingInPopulation'),
    })
  }
  if (hasInPopulation && !hasInclusion) {
    issues.push({
      severity: 'warning',
      message: t('validation.missingInclusionCriteria'),
    })
  }
  if (hasInPopulation && !hasExclusion) {
    issues.push({
      severity: 'info',
      message: t('validation.missingExclusionCriteria'),
    })
  }

  // Check empty parameter names
  for (const p of paramNames) {
    if (!p.trim()) {
      issues.push({ severity: 'error', message: t('validation.emptyParameterName') })
    }
  }

  // Check unused value sets
  // (This is a heuristic — we can't fully trace usage without CQL analysis)
  if (structure.valueSets.length > 0 && structure.expressions.length === 0 && structure.functions.length === 0) {
    issues.push({
      severity: 'info',
      message: t('validation.unusedValueSets', { count: structure.valueSets.length }),
    })
  }

  // Summary checks
  if (structure.includes.length === 0 && structure.expressions.length > 0) {
    issues.push({
      severity: 'info',
      message: t('validation.noLibraryIncludes'),
    })
  }

  // Check for mixed contexts (Patient vs Population)
  const contexts = new Set(structure.expressions.map((e) => e.context).filter(Boolean))
  if (contexts.size > 1) {
    issues.push({
      severity: 'info',
      message: t('validation.mixedContexts'),
    })
  }

  return issues
}

export default function ValidationPanel({ structure, parseError }: ValidationPanelProps) {
  const { t } = useTranslation('builder')
  const issues = useMemo(() => analyzeStructure(structure, t), [structure, t])

  const errorCount = issues.filter((i) => i.severity === 'error').length
  const warningCount = issues.filter((i) => i.severity === 'warning').length
  const infoCount = issues.filter((i) => i.severity === 'info').length

  if (parseError) {
    return (
      <Alert severity="error" sx={{ m: 1, py: 0.5 }} icon={<WarningIcon fontSize="small" />}>
        <Typography variant="caption">{parseError}</Typography>
      </Alert>
    )
  }

  if (issues.length === 0) {
    return (
      <Box sx={{ p: 1.5, textAlign: 'center' }}>
        <CheckIcon sx={{ color: 'success.main', fontSize: 20 }} />
        <Typography
          variant="caption"
          sx={{
            display: "block",
            color: "success.main",
            fontWeight: 600
          }}>
          {t('validation.noIssues')}
        </Typography>
      </Box>
    );
  }

  return (
    <Stack spacing={0.5} sx={{ p: 0.5 }}>
      <Stack direction="row" spacing={0.5} sx={{ mb: 0.5 }}>
        {errorCount > 0 && <Chip label={`${errorCount} ${t('validation.errors')}`} size="small" color="error" sx={{ height: 20, fontSize: '0.65rem' }} />}
        {warningCount > 0 && <Chip label={`${warningCount} ${t('validation.warnings')}`} size="small" color="warning" sx={{ height: 20, fontSize: '0.65rem' }} />}
        {infoCount > 0 && <Chip label={`${infoCount} ${t('validation.hints')}`} size="small" sx={{ height: 20, fontSize: '0.65rem', bgcolor: 'info.50', color: 'info.main' }} />}
      </Stack>
      {issues.map((issue, i) => (
        <Alert
          key={i}
          severity={issue.severity}
          icon={issue.severity === 'info' ? <InfoIcon sx={{ fontSize: 16 }} /> : <WarningIcon sx={{ fontSize: 16 }} />}
          sx={{
            py: 0,
            px: 1,
            '& .MuiAlert-icon': { py: 0.5, mr: 0.5 },
            '& .MuiAlert-message': { py: 0.25 },
          }}
        >
          <Typography variant="caption" sx={{ fontSize: '0.7rem', lineHeight: 1.4 }}>
            {issue.message}
          </Typography>
        </Alert>
      ))}
    </Stack>
  )
}
