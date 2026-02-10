import {
  Box, Stack, Typography, Card, CardContent, Chip, Divider, List, ListItem, ListItemText,
} from '@mui/material'
import type { Artifact } from '../../../types/authoring'

interface ArtifactSummaryViewProps {
  artifact: Artifact
}

export default function ArtifactSummaryView({ artifact }: ArtifactSummaryViewProps) {
  const includeCount = artifact.expTreeInclude?.childInstances?.length || 0
  const excludeCount = artifact.expTreeExclude?.childInstances?.length || 0
  const subpopCount = (artifact.subpopulations || []).filter((sp) => !sp.special).length
  const recCount = (artifact.recommendations || []).length
  const paramCount = (artifact.parameters || []).length
  const baseElCount = (artifact.baseElements || []).length
  const hasErrorStatement = !!(artifact.errorStatement?.ifThenClauses?.length || artifact.errorStatement?.elseClause)

  return (
    <Box>
      <Typography variant="h6" sx={{ mb: 2 }}>Artifact Summary</Typography>

      {/* Metadata */}
      <Card variant="outlined" sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>Metadata</Typography>
          <Stack spacing={0.5}>
            <MetadataRow label="Name" value={artifact.name} />
            <MetadataRow label="Version" value={artifact.version} />
            <MetadataRow label="Status" value={artifact.status} chip />
            <MetadataRow label="FHIR Version" value={artifact.fhirVersion} />
            {artifact.description && <MetadataRow label="Description" value={artifact.description} />}
            {artifact.publisher && <MetadataRow label="Publisher" value={artifact.publisher} />}
            {artifact.purpose && <MetadataRow label="Purpose" value={artifact.purpose} />}
          </Stack>
        </CardContent>
      </Card>

      {/* Logic Overview */}
      <Card variant="outlined" sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>Logic Overview</Typography>
          <Stack direction="row" spacing={2} flexWrap="wrap" sx={{ mb: 1 }}>
            <StatBadge label="Inclusion Elements" count={includeCount} />
            <StatBadge label="Exclusion Elements" count={excludeCount} />
            <StatBadge label="Subpopulations" count={subpopCount} />
            <StatBadge label="Base Elements" count={baseElCount} />
            <StatBadge label="Parameters" count={paramCount} />
          </Stack>

          {includeCount > 0 && (
            <Box sx={{ mt: 1.5 }}>
              <Typography variant="caption" color="text.secondary">Inclusion Criteria ({includeCount} element{includeCount !== 1 ? 's' : ''})</Typography>
              <List dense>
                {artifact.expTreeInclude.childInstances.map((el) => (
                  <ListItem key={el.uniqueId} sx={{ py: 0 }}>
                    <ListItemText
                      primary={el.name}
                      secondary={`Type: ${el.type} | Return: ${el.returnType}`}
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
              <Typography variant="caption" color="text.secondary">Exclusion Criteria ({excludeCount} element{excludeCount !== 1 ? 's' : ''})</Typography>
              <List dense>
                {artifact.expTreeExclude.childInstances.map((el) => (
                  <ListItem key={el.uniqueId} sx={{ py: 0 }}>
                    <ListItemText
                      primary={el.name}
                      secondary={`Type: ${el.type} | Return: ${el.returnType}`}
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
              Recommendations ({recCount})
            </Typography>
            <List dense>
              {artifact.recommendations.map((rec, i) => (
                <ListItem key={rec.uid} sx={{ py: 0.5, flexDirection: 'column', alignItems: 'flex-start' }}>
                  <Stack direction="row" spacing={1} alignItems="center">
                    <Chip label={`#${i + 1}`} size="small" />
                    {rec.grade && <Chip label={`Grade ${rec.grade}`} size="small" variant="outlined" />}
                  </Stack>
                  <Typography variant="body2" sx={{ mt: 0.5 }}>
                    {rec.text || '(No text)'}
                  </Typography>
                  {rec.subpopulations && rec.subpopulations.length > 0 && (
                    <Stack direction="row" spacing={0.5} sx={{ mt: 0.5 }}>
                      <Typography variant="caption" color="text.secondary">Applies to:</Typography>
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
            <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1 }}>Error Handling</Typography>
            {artifact.errorStatement?.ifThenClauses?.map((clause, i) => (
              <Typography key={i} variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                {i === 0 ? 'if' : 'else if'} {clause.ifCondition.label}: &quot;{clause.thenClause}&quot;
              </Typography>
            ))}
            {artifact.errorStatement?.elseClause && (
              <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                else: &quot;{artifact.errorStatement.elseClause}&quot;
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
