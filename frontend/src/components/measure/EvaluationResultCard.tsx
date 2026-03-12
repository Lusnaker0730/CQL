import { useState } from 'react'
import {
  Box,
  Typography,
  Card,
  CardContent,
  Stack,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  Collapse,
  IconButton,
  Alert,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  ExpandLess as ExpandLessIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { MeasureEvaluationResult, PopulationResult, StratifierResult } from '../../types'
import { getScoreChipColor, getScoreHex } from '../../utils/scoreColors'

interface EvaluationResultCardProps {
  result: MeasureEvaluationResult
}

const POPULATION_LABEL_KEYS: Record<string, string> = {
  'initial-population': 'evaluationResult.populationLabels.initial-population',
  denominator: 'evaluationResult.populationLabels.denominator',
  'denominator-exclusion': 'evaluationResult.populationLabels.denominator-exclusion',
  'denominator-exception': 'evaluationResult.populationLabels.denominator-exception',
  numerator: 'evaluationResult.populationLabels.numerator',
  'numerator-exclusion': 'evaluationResult.populationLabels.numerator-exclusion',
  'measure-population': 'evaluationResult.populationLabels.measure-population',
  'measure-population-exclusion': 'evaluationResult.populationLabels.measure-population-exclusion',
  'measure-observation': 'evaluationResult.populationLabels.measure-observation',
}

export default function EvaluationResultCard({ result }: EvaluationResultCardProps) {
  const { t } = useTranslation('measures')
  const [stratExpanded, setStratExpanded] = useState(false)

  function getPopulationLabel(type: string): string {
    const key = POPULATION_LABEL_KEYS[type]
    return key ? t(key) : type
  }

  return (
    <Card variant="outlined">
      <CardContent>
        <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
          <Box>
            <Typography variant="h6" sx={{ color: 'secondary.main' }}>
              {result.measureName || result.measureId}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {result.periodStart} - {result.periodEnd}
            </Typography>
          </Box>
          <Chip
            label={result.status}
            color={result.status === 'complete' ? 'success' : result.status === 'error' ? 'error' : 'warning'}
          />
        </Stack>

        {result.status === 'error' && result.errorMessage && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {result.errorMessage}
          </Alert>
        )}

        {result.groups?.map((group) => (
          <Box key={group.groupId} mb={2}>
            <Typography variant="subtitle1" gutterBottom sx={{ color: 'text.primary' }}>
              {group.description || t('evaluationResult.groupLabel', { groupId: group.groupId })}
            </Typography>

            {group.measureScore !== undefined && group.measureScore !== null && (
              <Box mb={3} sx={{ textAlign: 'center' }}>
                <Typography
                  sx={{
                    fontSize: '3rem',
                    fontWeight: 700,
                    color: getScoreHex(group.measureScore),
                    lineHeight: 1.1,
                  }}
                >
                  {group.measureScore.toFixed(1)}%
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ mb: 1.5, display: 'block' }}>
                  {t('evaluationResult.measureScore')}
                </Typography>
                <LinearProgress
                  variant="determinate"
                  value={Math.min(group.measureScore, 100)}
                  color={getScoreChipColor(group.measureScore)}
                  sx={{
                    height: 10,
                    borderRadius: 5,
                    bgcolor: 'rgba(13,115,119,0.08)',
                  }}
                />
              </Box>
            )}

            <TableContainer>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell scope="col">{t('evaluationResult.tableHeaders.population')}</TableCell>
                    <TableCell scope="col" align="right">{t('evaluationResult.tableHeaders.count')}</TableCell>
                    <TableCell scope="col">{t('evaluationResult.tableHeaders.subjects')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {group.populations?.map((pop: PopulationResult) => (
                    <TableRow key={pop.populationId}>
                      <TableCell>
                        <Typography variant="body2" sx={{ color: 'text.primary', fontWeight: 500 }}>
                          {getPopulationLabel(pop.populationType)}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Chip
                          label={pop.count ?? t('evaluationResult.na')}
                          size="small"
                          color={pop.count && pop.count > 0 ? 'primary' : 'default'}
                        />
                      </TableCell>
                      <TableCell>
                        {pop.subjectIds && pop.subjectIds.length > 0 && (
                          <Typography variant="caption">
                            {pop.subjectIds.join(', ')}
                          </Typography>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>

            {group.stratifiers && group.stratifiers.length > 0 && (
              <Box mt={2}>
                <Stack
                  direction="row"
                  alignItems="center"
                  spacing={1}
                  onClick={() => setStratExpanded(!stratExpanded)}
                  sx={{ cursor: 'pointer' }}
                >
                  <IconButton size="small" aria-label={t('evaluationResult.toggleStratification')}>
                    {stratExpanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
                  </IconButton>
                  <Typography variant="subtitle2">
                    {t('evaluationResult.stratification', { count: group.stratifiers.length })}
                  </Typography>
                </Stack>
                <Collapse in={stratExpanded}>
                  <TableContainer sx={{ mt: 1 }}>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell scope="col">{t('evaluationResult.stratificationHeaders.stratum')}</TableCell>
                          <TableCell scope="col">{t('evaluationResult.stratificationHeaders.value')}</TableCell>
                          <TableCell scope="col" align="right">{t('evaluationResult.stratificationHeaders.score')}</TableCell>
                          <TableCell scope="col">{t('evaluationResult.stratificationHeaders.populations')}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {group.stratifiers.map((strat: StratifierResult, idx: number) => (
                          <TableRow key={idx}>
                            <TableCell>
                              <Typography variant="body2" fontWeight={500}>
                                {strat.strataId}
                              </Typography>
                            </TableCell>
                            <TableCell>
                              <Chip label={strat.strataValue} size="small" />
                            </TableCell>
                            <TableCell align="right">
                              {strat.measureScore != null && (
                                <Typography
                                  variant="body2"
                                  sx={{ color: getScoreHex(strat.measureScore), fontWeight: 600 }}
                                >
                                  {strat.measureScore.toFixed(1)}%
                                </Typography>
                              )}
                            </TableCell>
                            <TableCell>
                              <Stack direction="row" spacing={0.5} flexWrap="wrap">
                                {strat.populations?.map((pop, pidx) => (
                                  <Chip
                                    key={pidx}
                                    label={`${getPopulationLabel(pop.populationType)}: ${pop.count ?? 0}`}
                                    size="small"
                                    variant="outlined"
                                  />
                                ))}
                              </Stack>
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Collapse>
              </Box>
            )}
          </Box>
        ))}

        {result.supplementalData && Object.keys(result.supplementalData).length > 0 && (
          <Box mt={2}>
            <Typography variant="subtitle2" gutterBottom>
              {t('evaluationResult.supplementalData')}
            </Typography>
            <Box
              component="pre"
              sx={{
                p: 2,
                bgcolor: 'action.hover',
                borderRadius: '8px',
                border: '1px solid',
                borderColor: 'divider',
                fontSize: '0.75rem',
                overflow: 'auto',
                fontFamily: '"Consolas", monospace',
                color: 'text.primary',
              }}
            >
              {JSON.stringify(result.supplementalData, null, 2)}
            </Box>
          </Box>
        )}
      </CardContent>
    </Card>
  )
}
