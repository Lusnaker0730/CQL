import {
  Box,
  Typography,
  Stack,
  Chip,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Alert,
  CircularProgress,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material'
import {
  ExpandMore as ExpandMoreIcon,
  Storage as StorageIcon,
  FilterList as FilterIcon,
} from '@mui/icons-material'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import type { MeasureDefinition, DataRequirementInfo } from '../../types'
import { measureApi } from '../../api'
import { helpContent } from '../../constants/helpContent'
import HelpTooltip from '../common/HelpTooltip'
import { extractApiError } from '../../utils/errorUtils'

interface DataRequirementsTabProps {
  measure: MeasureDefinition
}

export default function DataRequirementsTab({ measure }: DataRequirementsTabProps) {
  const { t } = useTranslation('measures')
  const { data: requirements = [], isLoading, isError, error } = useQuery({
    queryKey: ['data-requirements', measure.id],
    queryFn: () => measureApi.getDataRequirements(measure.id!),
    enabled: !!measure.id && !!measure.cqlContent,
  })

  // Group requirements by resource type
  const grouped = requirements.reduce<Record<string, DataRequirementInfo[]>>((acc, req) => {
    const key = req.type
    if (!acc[key]) acc[key] = []
    acc[key].push(req)
    return acc
  }, {})

  const resourceTypeCount = Object.keys(grouped).length
  const valueSetCount = requirements.reduce((count, req) => {
    return count + (req.codeFilter?.filter(cf => cf.valueSet || cf.codeSystemUrl)?.length ?? 0)
  }, 0)

  if (!measure.cqlContent) {
    return (
      <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
        <Stack direction="row" spacing={0.5} alignItems="center" mb={1}>
          <Typography variant="h6">{t('dataRequirements.title')}</Typography>
          <HelpTooltip text={helpContent.measures.dataRequirements} />
        </Stack>
        <Alert severity="info">
          {t('dataRequirements.noCql')}
        </Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" spacing={0.5} alignItems="center" mb={1}>
        <Typography variant="h6">{t('dataRequirements.title')}</Typography>
        <HelpTooltip text={helpContent.measures.dataRequirements} />
      </Stack>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && (
        <Alert severity="error">
          {t('dataRequirements.extractionError', { error: extractApiError(error) })}
        </Alert>
      )}

      {!isLoading && !isError && requirements.length === 0 && (
        <Alert severity="info">
          {t('dataRequirements.noRequirements')}
        </Alert>
      )}

      {!isLoading && requirements.length > 0 && (
        <>
          <Stack direction="row" spacing={1} mb={2}>
            <Chip
              icon={<StorageIcon />}
              label={t('dataRequirements.resourceTypes', { count: resourceTypeCount })}
              size="small"
              color="primary"
              variant="outlined"
            />
            <Chip
              icon={<FilterIcon />}
              label={t('dataRequirements.valueSets', { count: valueSetCount })}
              size="small"
              color="secondary"
              variant="outlined"
            />
            <Chip
              label={t('dataRequirements.totalRequirements', { count: requirements.length })}
              size="small"
              variant="outlined"
            />
          </Stack>

          {Object.entries(grouped).map(([resourceType, reqs]) => (
            <Accordion key={resourceType} defaultExpanded>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Stack direction="row" spacing={1} alignItems="center">
                  <Chip label={resourceType} size="small" color="primary" />
                  <Typography variant="body2" color="text.secondary">
                    {t('dataRequirements.requirementCount', { count: reqs.length })}
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <TableContainer component={Paper} variant="outlined" sx={{ m: 1 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>{t('dataRequirements.tableHeaders.filterType')}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{t('dataRequirements.tableHeaders.path')}</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>{t('dataRequirements.tableHeaders.valueSetCodes')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {reqs.map((req, idx) => (
                        <RequirementRows key={idx} requirement={req} />
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              </AccordionDetails>
            </Accordion>
          ))}
        </>
      )}
    </Box>
  )
}

function RequirementRows({ requirement }: { requirement: DataRequirementInfo }) {
  const { t } = useTranslation('measures')
  const hasCodeFilter = requirement.codeFilter && requirement.codeFilter.length > 0
  const hasDateFilter = requirement.dateFilter && requirement.dateFilter.length > 0

  if (!hasCodeFilter && !hasDateFilter) {
    return (
      <TableRow>
        <TableCell colSpan={3}>
          <Typography variant="body2" color="text.secondary" fontStyle="italic">
            {t('dataRequirements.noFilters', { type: requirement.type })}
          </Typography>
        </TableCell>
      </TableRow>
    )
  }

  return (
    <>
      {requirement.codeFilter?.map((cf, i) => (
        <TableRow key={`code-${i}`}>
          <TableCell>
            <Chip label={t('dataRequirements.filterTypes.code')} size="small" variant="outlined" color="info" sx={{ fontSize: '0.75rem' }} />
          </TableCell>
          <TableCell>
            <Typography variant="body2" fontFamily="monospace">{cf.path}</Typography>
          </TableCell>
          <TableCell>
            {cf.valueSet && (
              <Stack spacing={0.5}>
                <Typography variant="body2" fontWeight={500}>
                  {cf.valueSetName || t('dataRequirements.filterTypes.valueSet')}
                </Typography>
                <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                  {cf.valueSet}
                </Typography>
              </Stack>
            )}
            {!cf.valueSet && cf.codeSystemUrl && (
              <Stack spacing={0.5}>
                <Stack direction="row" spacing={0.5} alignItems="center">
                  <Chip
                    label={t('dataRequirements.filterTypes.codeSystem')}
                    size="small"
                    variant="outlined"
                    color="success"
                    sx={{ fontSize: '0.7rem' }}
                  />
                  {cf.codeSystemName && (
                    <Typography variant="body2" fontWeight={500}>
                      {cf.codeSystemName}
                    </Typography>
                  )}
                </Stack>
                <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                  {cf.codeSystemUrl}
                </Typography>
              </Stack>
            )}
            {cf.code && cf.code.length > 0 && (
              <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                {cf.code.map((c, j) => (
                  <Chip
                    key={j}
                    label={c.display || c.code || 'code'}
                    size="small"
                    variant="outlined"
                    title={c.system ? `${c.system}|${c.code}` : c.code}
                    sx={{ fontSize: '0.7rem' }}
                  />
                ))}
              </Stack>
            )}
          </TableCell>
        </TableRow>
      ))}
      {requirement.dateFilter?.map((df, i) => (
        <TableRow key={`date-${i}`}>
          <TableCell>
            <Chip label={t('dataRequirements.filterTypes.date')} size="small" variant="outlined" color="warning" sx={{ fontSize: '0.75rem' }} />
          </TableCell>
          <TableCell>
            <Typography variant="body2" fontFamily="monospace">{df.path}</Typography>
          </TableCell>
          <TableCell>
            <Typography variant="body2" color="text.secondary" fontStyle="italic">
              {t('dataRequirements.filterTypes.dateFilter')}
            </Typography>
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}
