import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Box,
  Chip,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import {
  CheckCircle as TrueIcon,
  Cancel as FalseIcon,
  RemoveCircleOutlined as UnknownIcon,
  ExpandMore as ExpandMoreIcon,
} from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import type { PopulationMembershipTrace, PopulationTraceEntry, GroupTrace } from '../../types'

interface Props {
  trace: PopulationMembershipTrace
}

/**
 * Renders per-group population membership trace answering
 * "why did this patient (not) end up in the Numerator?".
 * Shows raw CQL result, effective result after hierarchy gating, and reason.
 */
export default function PopulationTracePanel({ trace }: Props) {
  const { t } = useTranslation('measures')
  const groups = trace.groups ?? []

  if (groups.length === 0) {
    return null
  }

  if (groups.length === 1) {
    return <GroupContent group={groups[0]} />
  }

  return (
    <Stack spacing={1}>
      {groups.map((group, idx) => (
        <Accordion key={group.groupId ?? idx} defaultExpanded={idx === 0}>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="body2" sx={{
              fontWeight: 600
            }}>
              {group.groupId ?? t('populationTrace.defaultGroupName', { index: idx + 1 })}
              {group.scoringType && (
                <Chip size="small" sx={{ ml: 1 }} label={group.scoringType} variant="outlined" />
              )}
            </Typography>
          </AccordionSummary>
          <AccordionDetails>
            <GroupContent group={group} />
          </AccordionDetails>
        </Accordion>
      ))}
    </Stack>
  );
}

function GroupContent({ group }: { group: GroupTrace }) {
  const { t } = useTranslation('measures')
  const { populations } = group
  return (
    <Box>
      {group.description && (
        <Typography
          variant="caption"
          sx={{
            color: "text.secondary",
            display: 'block',
            mb: 1
          }}>
          {group.description}
        </Typography>
      )}
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('populationTrace.columns.population')}</TableCell>
              <TableCell align="center" sx={{ width: 72 }}>
                {t('populationTrace.columns.raw')}
              </TableCell>
              <TableCell align="center" sx={{ width: 88 }}>
                {t('populationTrace.columns.effective')}
              </TableCell>
              <TableCell>{t('populationTrace.columns.reason')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {populations.map((entry) => (
              <EntryRow key={entry.populationType} entry={entry} />
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  );
}

function EntryRow({ entry }: { entry: PopulationTraceEntry }) {
  const { t } = useTranslation('measures')
  // Indent nested populations visually
  const indentLevel = getIndentLevel(entry.populationType)
  // Grey out: raw was true but effective became false (gated / excluded)
  const dimmed = entry.rawResult === true && entry.effectiveResult === false

  return (
    <TableRow hover sx={{ opacity: dimmed ? 0.65 : 1 }}>
      <TableCell>
        <Box sx={{ pl: indentLevel * 2, display: 'flex', alignItems: 'center', gap: 0.5 }}>
          <Typography variant="body2" sx={{
            fontWeight: 500
          }}>
            {entry.displayName}
          </Typography>
          {entry.criteriaExpression && entry.criteriaExpression !== entry.displayName && (
            <Typography
              variant="caption"
              sx={{
                color: "text.secondary",
                fontFamily: 'monospace'
              }}>
              ← {entry.criteriaExpression}
            </Typography>
          )}
        </Box>
      </TableCell>
      <TableCell align="center">
        <BooleanIcon value={entry.rawResult} />
      </TableCell>
      <TableCell align="center">
        <BooleanIcon value={entry.effectiveResult} emphasis />
      </TableCell>
      <TableCell>
        <Typography variant="caption" sx={{
          color: "text.secondary"
        }}>
          {t(`populationTrace.reasons.${entry.reasonCode}`, {
            defaultValue: entry.reasonCode,
            ...(entry.reasonInputs ?? {}),
          })}
        </Typography>
      </TableCell>
    </TableRow>
  );
}

function BooleanIcon({ value, emphasis }: { value: boolean | null | undefined; emphasis?: boolean }) {
  if (value === true) {
    return <TrueIcon fontSize="small" color={emphasis ? 'success' : 'action'} />
  }
  if (value === false) {
    return <FalseIcon fontSize="small" color={emphasis ? 'error' : 'action'} />
  }
  return <UnknownIcon fontSize="small" color="disabled" />
}

function getIndentLevel(populationType: string): number {
  switch (populationType) {
    case 'initial-population':
    case 'measure-population':
      return 0
    case 'denominator':
      return 1
    case 'numerator':
    case 'denominator-exclusion':
    case 'denominator-exception':
    case 'measure-population-exclusion':
      return 2
    case 'numerator-exclusion':
      return 3
    default:
      return 0
  }
}
