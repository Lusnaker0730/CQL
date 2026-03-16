import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  MenuItem,
  Button,
  IconButton,
  Tooltip,
} from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { FHIR_RESOURCE_TYPES, type FhirResourceType } from '../../constants/fhirResources'
import { extractCqlName } from '../../utils/cqlNames'
import CqlPreviewBox from './CqlPreviewBox'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import ModifierChainBuilder, { applyModifierChain, type ChainModifier } from './ModifierChainBuilder'

interface RetrieveBuilderProps {
  valueSets: string[]
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

type ResourceType = FhirResourceType

function generateDefinitionName(resourceType: string, terminology: string): string {
  const termName = extractCqlName(terminology)
  return `${termName} ${resourceType === 'Observation' ? 'Observations' : `${resourceType}s`}`
}

function generateCql(
  resourceType: string,
  terminology: string,
  definitionName: string,
  chainModifiers: ChainModifier[],
): string {
  const termName = extractCqlName(terminology)
  const alias = resourceType[0]
  const baseRetrieve = `[${resourceType}: "${termName}"]`

  if (chainModifiers.length === 0) {
    return `define "${definitionName}":\n  ${baseRetrieve}`
  }

  const expr = applyModifierChain(baseRetrieve, alias, resourceType, chainModifiers)
  return `define "${definitionName}":\n  ${expr}`
}

export default function RetrieveBuilder({ valueSets, codes, onInsert, onCancel }: RetrieveBuilderProps) {
  const { t } = useTranslation('builder')
  const copyToClipboard = useCopyToClipboard()
  const [resourceType, setResourceType] = useState<ResourceType>('Observation')
  const [terminology, setTerminology] = useState('')
  const [definitionName, setDefinitionName] = useState('')
  const [nameEdited, setNameEdited] = useState(false)
  const [chainModifiers, setChainModifiers] = useState<ChainModifier[]>([])

  const terminologyOptions = useMemo(() => {
    const vsOptions = valueSets.map((vs) => ({ raw: vs, label: `VS: ${extractCqlName(vs)}`, type: 'valueset' as const }))
    const codeOptions = codes.map((c) => ({ raw: c, label: `Code: ${extractCqlName(c)}`, type: 'code' as const }))
    return [...vsOptions, ...codeOptions]
  }, [valueSets, codes])

  const handleResourceChange = (rt: ResourceType) => {
    setResourceType(rt)
    // Reset modifiers that don't apply to the new resource type
    setChainModifiers([])
    if (!nameEdited && terminology) {
      setDefinitionName(generateDefinitionName(rt, terminology))
    }
  }

  const handleTerminologyChange = (raw: string) => {
    setTerminology(raw)
    if (!nameEdited) {
      setDefinitionName(generateDefinitionName(resourceType, raw))
    }
  }

  const cqlPreview = useMemo(
    () => terminology ? generateCql(resourceType, terminology, definitionName || 'Untitled', chainModifiers) : '',
    [resourceType, terminology, definitionName, chainModifiers]
  )

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
        {FHIR_RESOURCE_TYPES.map((rt) => (
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

      <ModifierChainBuilder
        modifiers={chainModifiers}
        onChange={setChainModifiers}
        resourceType={resourceType}
        valueSets={valueSets}
        codes={codes}
      />

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
              onClick={() => copyToClipboard(cqlPreview)}
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
