import { useTranslation } from 'react-i18next'
import {
  Box, Stack, Typography, Card, CardContent, Chip, Divider, List, ListItem, ListItemText,
} from '@mui/material'
import type { Artifact } from '../../../types/authoring'
import { ERROR_CONDITION_VALUE_TO_KEY } from '../../../constants/authoringConstants'

interface ArtifactSummaryViewProps {
  artifact: Artifact
}

export default function ArtifactSummaryView({ artifact }: ArtifactSummaryViewProps) {
  const { t } = useTranslation('authoring')
  const includeCount = artifact.expTreeInclude?.childInstances?.length || 0
  const excludeCount = artifact.expTreeExclude?.childInstances?.length || 0
  const subpopCount = (artifact.subpopulations || []).filter((sp) => !sp.special).length
  const recCount = (artifact.recommendations || []).length
  const paramCount = (artifact.parameters || []).length
  const baseElCount = (artifact.baseElements || []).length
  const hasErrorStatement = !!(artifact.errorStatement?.ifThenClauses?.length || artifact.errorStatement?.elseClause)

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>{t('summary.title')}</Typography>

      {/* Metadata */}
      <Card variant="outlined" sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>{t('summary.metadata')}</Typography>
          <Stack spacing={0.5}>
            <MetadataRow label={t('summary.name')} value={artifact.name} />
            <MetadataRow label={t('summary.version')} value={artifact.version} />
            <MetadataRow label={t('summary.status')} value={artifact.status} chip />
            <MetadataRow label={t('summary.fhirVersion')} value={artifact.fhirVersion} />
            {artifact.description && <MetadataRow label={t('summary.description')} value={artifact.description} />}
            {artifact.publisher && <MetadataRow label={t('summary.publisher')} value={artifact.publisher} />}
            {artifact.purpose && <MetadataRow label={t('summary.purpose')} value={artifact.purpose} />}
          </Stack>
        </CardContent>
      </Card>

      {/* Logic Overview */}
      <Card variant="outlined" sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>{t('summary.logicOverview')}</Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap" sx={{ mb: 1 }}>
            <StatBadge label={t('summary.inclusionElements')} count={includeCount} />
            <StatBadge label={t('summary.exclusionElements')} count={excludeCount} />
            <StatBadge label={t('summary.subpopulations')} count={subpopCount} />
            <StatBadge label={t('summary.baseElements')} count={baseElCount} />
            <StatBadge label={t('summary.parametersLabel')} count={paramCount} />
          </Stack>

          {includeCount > 0 && (
            <Box sx={{ mt: 1.5 }}>
              <Typography variant="caption" color="text.secondary">{t('summary.inclusionCriteria', { count: includeCount })}</Typography>
              <List dense>
                {artifact.expTreeInclude.childInstances.map((el) => (
                  <ListItem key={el.uniqueId} sx={{ py: 0 }}>
                    <ListItemText
                      primary={el.name}
                      secondary={t('summary.typeReturn', { type: el.type, returnType: el.returnType })}
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}

          {excludeCount > 0 && (
            <Box sx={{ mt: 1 }}>
              <Typography variant="caption" color="text.secondary">{t('summary.exclusionCriteria', { count: excludeCount })}</Typography>
              <List dense>
                {artifact.expTreeExclude.childInstances.map((el) => (
                  <ListItem key={el.uniqueId} sx={{ py: 0 }}>
                    <ListItemText
                      primary={el.name}
                      secondary={t('summary.typeReturn', { type: el.type, returnType: el.returnType })}
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Recommendations */}
      {recCount > 0 && (
        <Card variant="outlined" sx={{ mb: 2 }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>
              {t('summary.recommendations', { count: recCount })}
            </Typography>
            <List dense>
              {artifact.recommendations.map((rec, i) => (
                <ListItem key={rec.uid} sx={{ py: 0.5, flexDirection: 'column', alignItems: 'flex-start' }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip label={`#${i + 1}`} size="small" />
                    {rec.grade && <Chip label={t('summary.gradeLabel', { grade: rec.grade })} size="small" variant="outlined" />}
                    {rec.cdsCardMode && <Chip label={t('summary.cdsCard')} size="small" color="info" />}
                  </Stack>
                  <Typography variant="body2" sx={{ mt: 0.5 }}>
                    {rec.text || t('summary.noText')}
                  </Typography>
                  {rec.subpopulations && rec.subpopulations.length > 0 && (
                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">{t('summary.appliesTo')}</Typography>
                      {rec.subpopulations.map((sp) => (
                        <Chip key={sp.uniqueId} label={sp.subpopulationName} size="small" variant="outlined" />
                      ))}
                    </Stack>
                  )}
                  {i < recCount - 1 && <Divider sx={{ mt: 1, width: '100%' }} />}
                </ListItem>
              ))}
            </List>
          </CardContent>
        </Card>
      )}

      {/* Error Handling */}
      {hasErrorStatement && (
        <Card variant="outlined" sx={{ mb: 2 }}>
          <CardContent>
            <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>{t('summary.errorHandling')}</Typography>
            {artifact.errorStatement?.ifThenClauses?.map((clause, i) => {
              // Resolve the label from the stable wire value rather than
              // the persisted .label so locale switches reflect immediately.
              const key = ERROR_CONDITION_VALUE_TO_KEY[clause.ifCondition.value]
              const liveLabel = key ? t(key) : clause.ifCondition.label
              return (
                <Typography key={i} variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                  {i === 0 ? t('summary.ifLabel') : t('summary.elseIfLabel')} {liveLabel}: &quot;{clause.thenClause}&quot;
                </Typography>
              )
            })}
            {artifact.errorStatement?.elseClause && (
              <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                {t('summary.elseLabel')}: &quot;{artifact.errorStatement.elseClause}&quot;
              </Typography>
            )}
          </CardContent>
        </Card>
      )}
    </Box>
  )
}

function MetadataRow({ label, value, chip }: { label: string; value?: string; chip?: boolean }) {
  if (!value) return null
  return (
    <Stack direction="row" spacing={1} alignItems="center">
      <Typography variant="caption" color="text.secondary" sx={{ minWidth: 100 }}>{label}:</Typography>
      {chip ? <Chip label={value} size="small" /> : <Typography variant="body2">{value}</Typography>}
    </Stack>
  )
}

function StatBadge({ label, count }: { label: string; count: number }) {
  return (
    <Box sx={{ textAlign: 'center', px: 1.5, py: 0.5, borderRadius: 1, backgroundColor: 'action.hover' }}>
      <Typography variant="h6" sx={{ fontWeight: 600, lineHeight: 1 }}>{count}</Typography>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
    </Box>
  )
}
