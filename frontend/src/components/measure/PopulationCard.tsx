import {
  Box,
  Paper,
  Typography,
  Stack,
  TextField,
  MenuItem,
  IconButton,
  Chip,
  Tooltip,
  Radio,
  RadioGroup,
  FormControl,
  FormControlLabel,
  FormLabel,
} from '@mui/material'
import {
  Delete as DeleteIcon,
  Warning as WarningIcon,
} from '@mui/icons-material'
import HelpTooltip from '../common/HelpTooltip'
import { getPopulationLabel, getPopulationConfig } from '../../constants/populationConfig'
import type { PopulationDefinition } from '../../types'

export interface CqlExpression {
  name: string
  context: string
  accessLevel: string
  resultType: string | null
}

interface PopulationCardProps {
  population: PopulationDefinition
  isRequired: boolean
  expressions: CqlExpression[]
  onChange: (field: keyof PopulationDefinition, value: string) => void
  onRemove: () => void
  canRemove: boolean
  showAssociationType?: boolean
  populationBasis?: string
}

function resultTypeLabel(rt: string | null): string {
  if (!rt) return 'unknown'
  const last = rt.split('.').pop() || rt
  return last.replace(/^System\./, '')
}

export default function PopulationCard({
  population,
  isRequired,
  expressions,
  onChange,
  onRemove,
  canRemove,
  showAssociationType,
  populationBasis,
}: PopulationCardProps) {
  const config = getPopulationConfig(population.populationType)
  const label = getPopulationLabel(population.populationType)
  const missingExpression = isRequired && !population.criteriaExpression

  const selectedExpr = expressions.find((e) => e.name === population.criteriaExpression)
  const basisIsBoolean = !populationBasis || populationBasis === 'Boolean'
  const nonBooleanSelected =
    basisIsBoolean &&
    selectedExpr && selectedExpr.resultType && !selectedExpr.resultType.toLowerCase().includes('boolean')

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        borderLeft: '3px solid',
        borderLeftColor: isRequired ? 'primary.main' : 'grey.400',
      }}
    >
      <Stack direction="row" justifyContent="space-between" alignItems="flex-start">
        <Stack direction="row" spacing={1} alignItems="center" mb={1}>
          <Typography variant="subtitle2" fontWeight={600}>
            {label}
          </Typography>
          <Chip
            label={isRequired ? 'Required' : 'Optional'}
            size="small"
            color={isRequired ? 'primary' : 'default'}
            variant={isRequired ? 'filled' : 'outlined'}
            sx={{ height: 20, fontSize: '0.7rem' }}
          />
          {config?.description && <HelpTooltip text={config.description} />}
          {missingExpression && (
            <Tooltip title="Required population is missing an expression">
              <WarningIcon color="warning" sx={{ fontSize: 18 }} />
            </Tooltip>
          )}
          {nonBooleanSelected && (
            <Tooltip title={`Expression returns ${resultTypeLabel(selectedExpr.resultType)} — Boolean expected for most populations`}>
              <WarningIcon color="info" sx={{ fontSize: 18 }} />
            </Tooltip>
          )}
        </Stack>
        <Tooltip title={canRemove ? 'Remove population' : 'Required populations cannot be removed'}>
          <span>
            <IconButton size="small" color="error" onClick={onRemove} disabled={!canRemove} aria-label="Remove population">
              <DeleteIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>

      {showAssociationType && (
        <FormControl sx={{ mb: 1.5 }}>
          <FormLabel sx={{ fontSize: '0.75rem' }}>Association</FormLabel>
          <RadioGroup
            row
            value={population.associationType || ''}
            onChange={(e) => onChange('associationType', e.target.value)}
          >
            <FormControlLabel value="Denominator" control={<Radio size="small" />} label="Denominator" />
            <FormControlLabel value="Numerator" control={<Radio size="small" />} label="Numerator" />
          </RadioGroup>
        </FormControl>
      )}

      <Box>
        <TextField
          select={expressions.length > 0}
          label="CQL Expression"
          size="small"
          fullWidth
          value={population.criteriaExpression}
          onChange={(e) => onChange('criteriaExpression', e.target.value)}
        >
          <MenuItem value="">
            <em>None</em>
          </MenuItem>
          {expressions.map((expr) => (
            <MenuItem key={expr.name} value={expr.name}>
              <Stack direction="row" spacing={1} alignItems="center" width="100%">
                <span>{expr.name}</span>
                {expr.resultType && (
                  <Chip
                    label={resultTypeLabel(expr.resultType)}
                    size="small"
                    color={expr.resultType.toLowerCase().includes('boolean') ? 'success' : 'default'}
                    variant="outlined"
                    sx={{ height: 18, fontSize: '0.65rem', ml: 'auto' }}
                  />
                )}
              </Stack>
            </MenuItem>
          ))}
        </TextField>
      </Box>
    </Paper>
  )
}
