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
import type { MeasureDefinition, DataRequirementInfo } from '../../types'
import { measureApi } from '../../api'
import { helpContent } from '../../constants/helpContent'
import HelpTooltip from '../common/HelpTooltip'

interface DataRequirementsTabProps {
  measure: MeasureDefinition
}

export default function DataRequirementsTab({ measure }: DataRequirementsTabProps) {
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
    return count + (req.codeFilter?.filter(cf => cf.valueSet)?.length ?? 0)
  }, 0)

  if (!measure.cqlContent) {
    return (
      <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
        <Stack direction="row" spacing={0.5} alignItems="center" mb={1}>
          <Typography variant="h6">Data Requirements</Typography>
          <HelpTooltip text={helpContent.measures.dataRequirements} />
        </Stack>
        <Alert severity="info">
          No CQL content defined for this measure. Add CQL logic in the CQL tab to see data requirements.
        </Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 2, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" spacing={0.5} alignItems="center" mb={1}>
        <Typography variant="h6">Data Requirements</Typography>
        <HelpTooltip text={helpContent.measures.dataRequirements} />
      </Stack>

      {isLoading && (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
          <CircularProgress />
        </Box>
      )}

      {isError && (
        <Alert severity="error">
          Failed to extract data requirements: {(error as Error).message}
        </Alert>
      )}

      {!isLoading && !isError && requirements.length === 0 && (
        <Alert severity="info">
          No data requirements found. The CQL may not contain any Retrieve expressions, or translation may have failed.
        </Alert>
      )}

      {!isLoading && requirements.length > 0 && (
        <>
          <Stack direction="row" spacing={1} mb={2}>
            <Chip
              icon={<StorageIcon />}
              label={`${resourceTypeCount} Resource Type${resourceTypeCount !== 1 ? 's' : ''}`}
              size="small"
              color="primary"
              variant="outlined"
            />
            <Chip
              icon={<FilterIcon />}
              label={`${valueSetCount} Value Set${valueSetCount !== 1 ? 's' : ''}`}
              size="small"
              color="secondary"
              variant="outlined"
            />
            <Chip
              label={`${requirements.length} Total Requirement${requirements.length !== 1 ? 's' : ''}`}
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
                    {reqs.length} requirement{reqs.length !== 1 ? 's' : ''}
                  </Typography>
                </Stack>
              </AccordionSummary>
              <AccordionDetails sx={{ p: 0 }}>
                <TableContainer component={Paper} variant="outlined" sx={{ m: 1 }}>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell sx={{ fontWeight: 600 }}>Filter Type</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Path</TableCell>
                        <TableCell sx={{ fontWeight: 600 }}>Value Set / Codes</TableCell>
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
  const hasCodeFilter = requirement.codeFilter && requirement.codeFilter.length > 0
  const hasDateFilter = requirement.dateFilter && requirement.dateFilter.length > 0

  if (!hasCodeFilter && !hasDateFilter) {
    return (
      <TableRow>
        <TableCell colSpan={3}>
          <Typography variant="body2" color="text.secondary" fontStyle="italic">
            No filters (retrieves all {requirement.type} resources)
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
            <Chip label="Code" size="small" variant="outlined" color="info" sx={{ fontSize: '0.75rem' }} />
          </TableCell>
          <TableCell>
            <Typography variant="body2" fontFamily="monospace">{cf.path}</Typography>
          </TableCell>
          <TableCell>
            {cf.valueSet && (
              <Stack spacing={0.5}>
                <Typography variant="body2" fontWeight={500}>
                  {cf.valueSetName || 'Value Set'}
                </Typography>
                <Typography variant="caption" color="text.secondary" fontFamily="monospace">
                  {cf.valueSet}
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
            <Chip label="Date" size="small" variant="outlined" color="warning" sx={{ fontSize: '0.75rem' }} />
          </TableCell>
          <TableCell>
            <Typography variant="body2" fontFamily="monospace">{df.path}</Typography>
          </TableCell>
          <TableCell>
            <Typography variant="body2" color="text.secondary" fontStyle="italic">
              Date filter
            </Typography>
          </TableCell>
        </TableRow>
      ))}
    </>
  )
}
