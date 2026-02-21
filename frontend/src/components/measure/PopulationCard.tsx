import { useTranslation } from 'react-i18next'
import {
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

function resultTypeLabel(rt: string | null, fallback: string = 'unknown'): string {
  if (!rt) return fallback
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
  const { t } = useTranslation('measures')
  const label = t(`populationCard.types.${population.populationType}`, population.populationType)
  const description = t(`populationCard.descriptions.${population.populationType}`, '')
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
            label={isRequired ? t('populationCard.required') : t('populationCard.optional')}
            size="small"
            color={isRequired ? 'primary' : 'default'}
            variant={isRequired ? 'filled' : 'outlined'}
            sx={{ height: 20, fontSize: '0.7rem' }}
          />
          {description && <HelpTooltip text={description} />}
          {missingExpression && (
            <Tooltip title={t('populationCard.missingExpression')}>
              <WarningIcon color="warning" sx={{ fontSize: 18 }} />
            </Tooltip>
          )}
          {nonBooleanSelected && (
            <Tooltip title={t('populationCard.nonBooleanWarning', { type: resultTypeLabel(selectedExpr.resultType, t('populationCard.unknown')) })}>
              <WarningIcon color="info" sx={{ fontSize: 18 }} />
            </Tooltip>
          )}
        </Stack>
        <Tooltip title={canRemove ? t('populationCard.removePopulation') : t('populationCard.cannotRemove')}>
          <span>
            <IconButton size="small" color="error" onClick={onRemove} disabled={!canRemove} aria-label={t('populationCard.removePopulation')}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </span>
        </Tooltip>
      </Stack>

      {showAssociationType && (
        <FormControl sx={{ mb: 1.5 }}>
          <FormLabel sx={{ fontSize: '0.75rem' }}>{t('populationCard.association')}</FormLabel>
          <RadioGroup
            row
            value={population.associationType || ''}
            onChange={(e) => onChange('associationType', e.target.value)}
          >
            <FormControlLabel value="Denominator" control={<Radio size="small" />} label={t('populationCard.denominator')} />
            <FormControlLabel value="Numerator" control={<Radio size="small" />} label={t('populationCard.numerator')} />
          </RadioGroup>
        </FormControl>
      )}

      <Stack spacing={1.5}>
        <TextField
          select={expressions.length > 0}
          label={t('populationCard.cqlExpression')}
          size="small"
          fullWidth
          value={population.criteriaExpression}
          onChange={(e) => onChange('criteriaExpression', e.target.value)}
        >
          <MenuItem value="">
            <em>{t('populationCard.none')}</em>
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
        <TextField
          label={t('populationCard.description')}
          size="small"
          fullWidth
          multiline
          minRows={1}
          maxRows={3}
          value={population.description || ''}
          onChange={(e) => onChange('description', e.target.value)}
          placeholder={t('populationCard.descriptionPlaceholder')}
        />
      </Stack>
    </Paper>
  )
}
