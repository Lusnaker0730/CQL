import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  FormControlLabel,
  Checkbox,
  Button,
  IconButton,
  Tooltip,
} from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
import { useNotification } from '../../hooks/useNotification'

interface RetrieveBuilderProps {
  valueSets: string[]
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

const RESOURCE_TYPES = [
  'Observation',
  'Condition',
  'Procedure',
  'MedicationRequest',
  'MedicationStatement',
  'Encounter',
  'Immunization',
  'AllergyIntolerance',
  'Device',
  'ServiceRequest',
] as const

type ResourceType = (typeof RESOURCE_TYPES)[number]

// Which modifiers apply per resource type
const MODIFIER_CONFIG: Record<string, { mostRecent?: boolean; activeConfirmed?: boolean; lookBack?: boolean; exists?: boolean }> = {
  Observation: { mostRecent: true, lookBack: true, exists: true },
  Condition: { activeConfirmed: true, lookBack: true, exists: true },
  Procedure: { lookBack: true, exists: true },
  MedicationRequest: { activeConfirmed: true, lookBack: true, exists: true },
  MedicationStatement: { activeConfirmed: true, lookBack: true, exists: true },
  Encounter: { lookBack: true, exists: true },
  Immunization: { lookBack: true, exists: true },
  AllergyIntolerance: { activeConfirmed: true, lookBack: true, exists: true },
  Device: { lookBack: true, exists: true },
  ServiceRequest: { activeConfirmed: true, lookBack: true, exists: true },
}

/**
 * Parse a valueset/code name from the raw string (e.g. "Diabetes": 'http://...' → "Diabetes")
 */
function extractName(raw: string): string {
  const m = raw.match(/^"([^"]+)"/)
  return m ? m[1] : raw
}

function generateDefinitionName(resourceType: string, terminology: string, modifiers: Modifiers): string {
  const termName = extractName(terminology)
  const parts: string[] = []
  if (modifiers.activeConfirmed) parts.push('Active')
  if (modifiers.mostRecent) parts.push('Most Recent')
  parts.push(termName)
  parts.push(resourceType === 'Observation' ? 'Observations' : `${resourceType}s`)
  return parts.join(' ')
}

interface Modifiers {
  mostRecent: boolean
  activeConfirmed: boolean
  lookBack: boolean
  lookBackValue: string
  lookBackUnit: string
  exists: boolean
}

/** Date/time expression used for look-back filtering per resource type */
function getDateExpression(resourceType: string, alias: string): string {
  switch (resourceType) {
    case 'Observation':
    case 'MedicationStatement':
      return `${alias}.effective`
    case 'Condition':
    case 'AllergyIntolerance':
      return `${alias}.onset`
    case 'Procedure':
      return `${alias}.performed`
    case 'MedicationRequest':
    case 'ServiceRequest':
      return `${alias}.authoredOn`
    case 'Encounter':
      return `${alias}.period`
    case 'Immunization':
      return `${alias}.occurrence`
    default:
      return `${alias}.effective`
  }
}

function generateCql(
  resourceType: string,
  terminology: string,
  definitionName: string,
  modifiers: Modifiers,
): string {
  const termName = extractName(terminology)
  const alias = resourceType[0]
  const needsQuery = modifiers.activeConfirmed
    || (modifiers.lookBack && modifiers.lookBackValue)
    || modifiers.mostRecent

  // Simple retrieve — no query needed
  if (!needsQuery) {
    const retrieve = `[${resourceType}: "${termName}"]`
    if (modifiers.exists) return `define "${definitionName}":\n  exists ${retrieve}`
    return `define "${definitionName}":\n  ${retrieve}`
  }

  // Build inline query: where clauses + optional sort
  const whereClauses: string[] = []

  if (modifiers.activeConfirmed) {
    switch (resourceType) {
      case 'Condition':
      case 'AllergyIntolerance':
        whereClauses.push(`${alias}.clinicalStatus.coding.code contains 'active'`)
        break
      default:
        whereClauses.push(`${alias}.status = 'active'`)
    }
  }

  if (modifiers.lookBack && modifiers.lookBackValue) {
    const dateExpr = getDateExpression(resourceType, alias)
    whereClauses.push(`${dateExpr} >= Now() - ${modifiers.lookBackValue} ${modifiers.lookBackUnit}`)
  }

  const whereStr = whereClauses.length > 0
    ? `\n    where ${whereClauses.join('\n      and ')}`
    : ''
  const sortStr = modifiers.mostRecent
    ? `\n    sort by Coalesce(effective as dateTime, issued)`
    : ''

  const query = `[${resourceType}: "${termName}"] ${alias}${whereStr}${sortStr}`

  let expr: string
  if (modifiers.mostRecent) {
    expr = `Last(\n    ${query}\n  )`
    if (modifiers.exists) expr = `${expr} is not null`
  } else if (modifiers.exists) {
    expr = `exists (\n    ${query}\n  )`
  } else {
    expr = query
  }

  return `define "${definitionName}":\n  ${expr}`
}

export default function RetrieveBuilder({ valueSets, codes, onInsert, onCancel }: RetrieveBuilderProps) {
  const { t } = useTranslation('builder')
  const { showNotification } = useNotification()
  const [resourceType, setResourceType] = useState<ResourceType>('Observation')
  const [terminology, setTerminology] = useState('')
  const [definitionName, setDefinitionName] = useState('')
  const [nameEdited, setNameEdited] = useState(false)
  const [modifiers, setModifiers] = useState<Modifiers>({
    mostRecent: false,
    activeConfirmed: false,
    lookBack: false,
    lookBackValue: '',
    lookBackUnit: 'years',
    exists: true,
  })

  // Combine valueSets and codes as terminology sources
  const terminologyOptions = useMemo(() => {
    const vsOptions = valueSets.map((vs) => ({ raw: vs, label: `VS: ${extractName(vs)}`, type: 'valueset' as const }))
    const codeOptions = codes.map((c) => ({ raw: c, label: `Code: ${extractName(c)}`, type: 'code' as const }))
    return [...vsOptions, ...codeOptions]
  }, [valueSets, codes])

  const config = MODIFIER_CONFIG[resourceType] || {}

  const handleResourceChange = (rt: ResourceType) => {
    setResourceType(rt)
    // Reset modifiers that don't apply
    setModifiers((prev) => ({
      ...prev,
      mostRecent: config.mostRecent ? prev.mostRecent : false,
      activeConfirmed: config.activeConfirmed ? prev.activeConfirmed : false,
    }))
    if (!nameEdited && terminology) {
      setDefinitionName(generateDefinitionName(rt, terminology, modifiers))
    }
  }

  const handleTerminologyChange = (raw: string) => {
    setTerminology(raw)
    if (!nameEdited) {
      setDefinitionName(generateDefinitionName(resourceType, raw, modifiers))
    }
  }

  const handleModifierChange = (key: keyof Modifiers, value: boolean | string) => {
    const updated = { ...modifiers, [key]: value }
    setModifiers(updated)
    if (!nameEdited && terminology) {
      setDefinitionName(generateDefinitionName(resourceType, terminology, updated as Modifiers))
    }
  }

  // Live CQL preview
  const cqlPreview = terminology
    ? generateCql(resourceType, terminology, definitionName || 'Untitled', modifiers)
    : ''

  const handleInsert = () => {
    if (!terminology || !definitionName.trim()) return
    onInsert(cqlPreview)
  }

  return (
    <Stack spacing={1}>
      <TextField
        select
        size="small"
        label={t('retrieve.resourceType')}
        value={resourceType}
        onChange={(e) => handleResourceChange(e.target.value as ResourceType)}
      >
        {RESOURCE_TYPES.map((rt) => (
          <MenuItem key={rt} value={rt}>{rt}</MenuItem>
        ))}
      </TextField>

      <TextField
        select
        size="small"
        label={t('retrieve.terminologySource')}
        value={terminology}
        onChange={(e) => handleTerminologyChange(e.target.value)}
        SelectProps={{ displayEmpty: true }}
        InputLabelProps={{ shrink: true }}
      >
        <MenuItem value="" disabled>
          <em>{t('retrieve.selectTerminology')}</em>
        </MenuItem>
        {terminologyOptions.map((opt) => (
          <MenuItem key={opt.raw} value={opt.raw}>
            {opt.label}
          </MenuItem>
        ))}
      </TextField>

      <TextField
        size="small"
        label={t('retrieve.definitionName')}
        value={definitionName}
        onChange={(e) => {
          setDefinitionName(e.target.value)
          setNameEdited(true)
        }}
      />

      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('retrieve.modifiers')}
      </Typography>

      <Stack spacing={0} sx={{ pl: 0.5 }}>
        {config.mostRecent && (
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={modifiers.mostRecent}
                onChange={(e) => handleModifierChange('mostRecent', e.target.checked)}
              />
            }
            label={<Typography variant="body2">{t('retrieve.mostRecent')}</Typography>}
          />
        )}

        {config.activeConfirmed && (
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={modifiers.activeConfirmed}
                onChange={(e) => handleModifierChange('activeConfirmed', e.target.checked)}
              />
            }
            label={<Typography variant="body2">{t('retrieve.activeConfirmed')}</Typography>}
          />
        )}

        {config.lookBack && (
          <>
            <FormControlLabel
              control={
                <Checkbox
                  size="small"
                  checked={modifiers.lookBack}
                  onChange={(e) => handleModifierChange('lookBack', e.target.checked)}
                />
              }
              label={<Typography variant="body2">{t('retrieve.lookBackPeriod')}</Typography>}
            />
            {modifiers.lookBack && (
              <Stack direction="row" spacing={1} sx={{ pl: 3, pb: 0.5 }}>
                <TextField
                  size="small"
                  label={t('retrieve.value')}
                  value={modifiers.lookBackValue}
                  onChange={(e) => handleModifierChange('lookBackValue', e.target.value)}
                  sx={{ width: 80 }}
                />
                <TextField
                  select
                  size="small"
                  label={t('retrieve.unit')}
                  value={modifiers.lookBackUnit}
                  onChange={(e) => handleModifierChange('lookBackUnit', e.target.value)}
                  sx={{ width: 100 }}
                >
                  <MenuItem value="years">{t('retrieve.years')}</MenuItem>
                  <MenuItem value="months">{t('retrieve.months')}</MenuItem>
                  <MenuItem value="weeks">{t('retrieve.weeks')}</MenuItem>
                  <MenuItem value="days">{t('retrieve.days')}</MenuItem>
                </TextField>
              </Stack>
            )}
          </>
        )}

        {config.exists && (
          <FormControlLabel
            control={
              <Checkbox
                size="small"
                checked={modifiers.exists}
                onChange={(e) => handleModifierChange('exists', e.target.checked)}
              />
            }
            label={<Typography variant="body2">{t('retrieve.existsBoolean')}</Typography>}
          />
        )}
      </Stack>

      <CqlPreviewBox code={cqlPreview} />

      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton
          onClick={handleInsert}
          disabled={!terminology || !definitionName.trim()}
        >
          {t('common.insert')}
        </GradientButton>
        {cqlPreview && (
          <Tooltip title={t('common.copyToClipboard')}>
            <IconButton
              size="small"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(cqlPreview)
                  showNotification(t('common.copiedToClipboard'), 'success', 2000)
                } catch {
                  showNotification(t('common.copyFailed'), 'error', 2000)
                }
              }}
            >
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
      </Stack>
    </Stack>
  )
}
