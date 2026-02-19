import { useState } from 'react'
import {
  Box,
  Typography,
  Stack,
  Button,
  Alert,
  Chip,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  Divider,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Error as ErrorIcon,
  Warning as WarningIcon,
  Info as InfoIcon,
  CheckCircle as ValidIcon,
  Cancel as InvalidIcon,
  PlayArrow as RunIcon,
  Speed as QuickIcon,
  Code as CqlIcon,
  People as PopIcon,
  Description as MetaIcon,
  Science as TestIcon,
  VerifiedUser as QiCoreIcon,
  ArrowForward as FixIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useValidateMeasure, useQuickValidateMeasure } from '../../hooks/useMeasures'
import type { ValidationReport, ValidationIssue } from '../../types'

const SEVERITY_CONFIG = {
  ERROR: { icon: <ErrorIcon sx={{ fontSize: 18 }} />, color: 'error.main', chipColor: 'error' as const },
  WARNING: { icon: <WarningIcon sx={{ fontSize: 18 }} />, color: 'warning.main', chipColor: 'warning' as const },
  INFO: { icon: <InfoIcon sx={{ fontSize: 18 }} />, color: 'info.main', chipColor: 'info' as const },
}

const CATEGORY_CONFIG: Record<string, { label: string; icon: React.ReactElement }> = {
  CQL: { label: 'CQL Logic', icon: <CqlIcon sx={{ fontSize: 18 }} /> },
  POPULATIONS: { label: 'Populations', icon: <PopIcon sx={{ fontSize: 18 }} /> },
  METADATA: { label: 'Metadata', icon: <MetaIcon sx={{ fontSize: 18 }} /> },
  TEST_CASES: { label: 'Test Cases', icon: <TestIcon sx={{ fontSize: 18 }} /> },
  QI_CORE: { label: 'QI-Core', icon: <QiCoreIcon sx={{ fontSize: 18 }} /> },
}

interface MeasureValidationPanelProps {
  measureId: number | undefined
  onNavigateToTab?: (tabIndex: number) => void
}

export default function MeasureValidationPanel({ measureId, onNavigateToTab }: MeasureValidationPanelProps) {
  const [report, setReport] = useState<ValidationReport | null>(null)
  const validateMutation = useValidateMeasure()
  const quickValidateMutation = useQuickValidateMeasure()

  const handleFullValidate = () => {
    if (!measureId) return
    validateMutation.mutate(measureId, {
      onSuccess: (data) => setReport(data),
    })
  }

  const handleQuickValidate = () => {
    if (!measureId) return
    quickValidateMutation.mutate(measureId, {
      onSuccess: (data) => setReport(data),
    })
  }

  const isLoading = validateMutation.isPending || quickValidateMutation.isPending

  // Group issues by category
  const groupedIssues: Record<string, ValidationIssue[]> = {}
  if (report) {
    for (const issue of report.issues) {
      const cat = issue.category || 'OTHER'
      if (!groupedIssues[cat]) groupedIssues[cat] = []
      groupedIssues[cat].push(issue)
    }
  }

  const handleFix = (issue: ValidationIssue) => {
    if (!onNavigateToTab) return
    // Map category to tab index
    switch (issue.category) {
      case 'METADATA': onNavigateToTab(0); break       // Details
      case 'CQL': onNavigateToTab(1); break             // CQL
      case 'POPULATIONS': onNavigateToTab(3); break     // Population Criteria
      case 'TEST_CASES': onNavigateToTab(5); break      // Test Cases
      default: break
    }
  }

  return (
    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
        <Typography variant="h6">Measure Validation</Typography>
        <Stack direction="row" spacing={1}>
          <Button
            size="small"
            variant="outlined"
            startIcon={<QuickIcon />}
            onClick={handleQuickValidate}
            disabled={!measureId || isLoading}
            sx={{ textTransform: 'none' }}
          >
            Quick Check
          </Button>
          <GradientButton
            startIcon={isLoading ? <CircularProgress size={16} color="inherit" /> : <RunIcon />}
            onClick={handleFullValidate}
            disabled={!measureId || isLoading}
          >
            {isLoading ? 'Validating...' : 'Full Validation'}
          </GradientButton>
        </Stack>
      </Stack>

      {!report && !isLoading && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Click "Full Validation" to check your measure for completeness, CQL correctness, population logic, and QI-Core compliance.
          Use "Quick Check" for a faster CQL + population check.
        </Alert>
      )}

      {report && (
        <>
          {/* Summary Banner */}
          <Alert
            severity={report.valid ? 'success' : 'error'}
            icon={report.valid ? <ValidIcon /> : <InvalidIcon />}
            sx={{ mb: 2 }}
          >
            <Stack direction="row" spacing={2} alignItems="center">
              <Typography variant="body2" fontWeight={600}>
                {report.valid ? 'Validation Passed' : 'Validation Failed'}
              </Typography>
              <Stack direction="row" spacing={1}>
                {report.errorCount > 0 && (
                  <Chip label={`${report.errorCount} errors`} size="small" color="error" variant="outlined" />
                )}
                {report.warningCount > 0 && (
                  <Chip label={`${report.warningCount} warnings`} size="small" color="warning" variant="outlined" />
                )}
                {report.infoCount > 0 && (
                  <Chip label={`${report.infoCount} info`} size="small" color="info" variant="outlined" />
                )}
              </Stack>
              <Typography variant="caption" color="text.secondary">
                ({report.validationTimeMs}ms)
              </Typography>
            </Stack>
          </Alert>

          {/* Grouped Issues */}
          {Object.entries(CATEGORY_CONFIG).map(([catKey, catConfig]) => {
            const issues = groupedIssues[catKey]
            if (!issues || issues.length === 0) return null

            const errorCount = issues.filter(i => i.severity === 'ERROR').length
            const warnCount = issues.filter(i => i.severity === 'WARNING').length

            return (
              <Accordion key={catKey} defaultExpanded={errorCount > 0}>
                <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ width: '100%' }}>
                    {catConfig.icon}
                    <Typography variant="subtitle2" sx={{ flex: 1 }}>
                      {catConfig.label}
                    </Typography>
                    <Stack direction="row" spacing={0.5}>
                      {errorCount > 0 && (
                        <Chip label={errorCount} size="small" color="error" sx={{ height: 20, fontSize: '0.7rem' }} />
                      )}
                      {warnCount > 0 && (
                        <Chip label={warnCount} size="small" color="warning" sx={{ height: 20, fontSize: '0.7rem' }} />
                      )}
                      {errorCount === 0 && warnCount === 0 && (
                        <Chip label={issues.length} size="small" color="info" sx={{ height: 20, fontSize: '0.7rem' }} />
                      )}
                    </Stack>
                  </Stack>
                </AccordionSummary>
                <AccordionDetails sx={{ pt: 0 }}>
                  <Divider sx={{ mb: 1 }} />
                  <List disablePadding dense>
                    {issues.map((issue, idx) => {
                      const sevConfig = SEVERITY_CONFIG[issue.severity] || SEVERITY_CONFIG.INFO
                      return (
                        <ListItem key={idx} sx={{ px: 0, py: 0.5, alignItems: 'flex-start' }}>
                          <ListItemIcon sx={{ minWidth: 32, mt: 0.5, color: sevConfig.color }}>
                            {sevConfig.icon}
                          </ListItemIcon>
                          <ListItemText
                            primary={issue.message}
                            secondary={issue.fixHint}
                            primaryTypographyProps={{ variant: 'body2', fontSize: '0.85rem' }}
                            secondaryTypographyProps={{ variant: 'caption' }}
                          />
                          {issue.fixHint && onNavigateToTab && (
                            <Button
                              size="small"
                              endIcon={<FixIcon sx={{ fontSize: 14 }} />}
                              onClick={() => handleFix(issue)}
                              sx={{ textTransform: 'none', fontSize: '0.7rem', whiteSpace: 'nowrap', ml: 1 }}
                            >
                              Fix
                            </Button>
                          )}
                        </ListItem>
                      )
                    })}
                  </List>
                </AccordionDetails>
              </Accordion>
            )
          })}

          {/* No issues at all */}
          {report.issues.length === 0 && (
            <Alert severity="success" sx={{ mt: 2 }}>
              No issues found. Your measure looks good!
            </Alert>
          )}
        </>
      )}
    </Box>
  )
}
