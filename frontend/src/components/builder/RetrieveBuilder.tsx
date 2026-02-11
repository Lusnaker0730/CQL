import { useState, useMemo } from 'react'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  Box,
  FormControlLabel,
  Checkbox,
  Button,
  IconButton,
  Tooltip,
} from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
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

function generateCql(
  resourceType: string,
  terminology: string,
  definitionName: string,
  modifiers: Modifiers,
): string {
  const termName = extractName(terminology)
  let expr = `[${resourceType}: "${termName}"]`

  // Build nested C3F wrappers: innermost first
  // Order: activeConfirmed → lookBack → mostRecent → exists (outermost)
  if (modifiers.activeConfirmed) {
    switch (resourceType) {
      case 'Condition':
        expr = `C3F.ActiveCondition(${expr})`
        break
      case 'AllergyIntolerance':
        expr = `C3F.ActiveAllergyIntolerance(${expr})`
        break
      case 'MedicationRequest':
      case 'MedicationStatement':
        expr = `C3F.ActiveMedicationRequest(${expr})`
        break
      case 'ServiceRequest':
        expr = `C3F.ActiveServiceRequest(${expr})`
        break
      default:
        expr = `C3F.Active(${expr})`
    }
  }

  if (modifiers.lookBack && modifiers.lookBackValue) {
    expr = `C3F.LookBack(${expr}, ${modifiers.lookBackValue} ${modifiers.lookBackUnit})`
  }

  if (modifiers.mostRecent) {
    expr = `C3F.MostRecent(${expr})`
  }

  if (modifiers.exists) {
    expr = `exists(${expr})`
  }

  return `define "${definitionName}":\n  ${expr}`
}

export default function RetrieveBuilder({ valueSets, codes, onInsert, onCancel }: RetrieveBuilderProps) {
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
        label="Resource Type"
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
        label="Terminology Source"
        value={terminology}
        onChange={(e) => handleTerminologyChange(e.target.value)}
        SelectProps={{ displayEmpty: true }}
      >
        <MenuItem value="" disabled>
          <em>Select a valueset or code...</em>
        </MenuItem>
        {terminologyOptions.map((opt) => (
          <MenuItem key={opt.raw} value={opt.raw}>
            {opt.label}
          </MenuItem>
        ))}
      </TextField>

      <TextField
        size="small"
        label="Definition Name"
        value={definitionName}
        onChange={(e) => {
          setDefinitionName(e.target.value)
          setNameEdited(true)
        }}
      />

      <Typography variant="caption" fontWeight={600} color="text.secondary">
        Modifiers
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
            label={<Typography variant="body2">Most Recent</Typography>}
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
            label={<Typography variant="body2">Active / Confirmed</Typography>}
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
              label={<Typography variant="body2">Look Back Period</Typography>}
            />
            {modifiers.lookBack && (
              <Stack direction="row" spacing={1} sx={{ pl: 3, pb: 0.5 }}>
                <TextField
                  size="small"
                  label="Value"
                  value={modifiers.lookBackValue}
                  onChange={(e) => handleModifierChange('lookBackValue', e.target.value)}
                  sx={{ width: 80 }}
                />
                <TextField
                  select
                  size="small"
                  label="Unit"
                  value={modifiers.lookBackUnit}
                  onChange={(e) => handleModifierChange('lookBackUnit', e.target.value)}
                  sx={{ width: 100 }}
                >
                  <MenuItem value="years">years</MenuItem>
                  <MenuItem value="months">months</MenuItem>
                  <MenuItem value="weeks">weeks</MenuItem>
                  <MenuItem value="days">days</MenuItem>
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
            label={<Typography variant="body2">Exists (boolean)</Typography>}
          />
        )}
      </Stack>

      {cqlPreview && (
        <Box
          sx={{
            p: 1,
            bgcolor: 'rgba(27,58,92,0.04)',
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            fontFamily: 'monospace',
            fontSize: '0.75rem',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
            maxHeight: 120,
            overflow: 'auto',
          }}
        >
          {cqlPreview}
        </Box>
      )}

      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton
          onClick={handleInsert}
          disabled={!terminology || !definitionName.trim()}
        >
          Insert
        </GradientButton>
        {cqlPreview && (
          <Tooltip title="Copy to clipboard">
            <IconButton
              size="small"
              onClick={async () => {
                try {
                  await navigator.clipboard.writeText(cqlPreview)
                  showNotification('Copied to clipboard', 'success', 2000)
                } catch {
                  showNotification('Failed to copy', 'error', 2000)
                }
              }}
            >
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Button size="small" onClick={onCancel}>Cancel</Button>
      </Stack>
    </Stack>
  )
}
