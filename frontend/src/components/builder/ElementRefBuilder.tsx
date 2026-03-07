import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Typography,
  Button,
  ListSubheader,
  MenuItem,
  InputAdornment,
  Chip,
  Box,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import SnippetPreview from './SnippetPreview'
import TypeChip from './TypeChip'

interface ElementRefBuilderProps {
  expressions: { name: string; resultType?: string }[]
  parameters: string[]
  functions?: { name: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

interface ElementOption {
  value: string
  label: string
  resultType?: string
  category: 'definition' | 'parameter' | 'function'
}

interface ModifierPreset {
  labelKey: string
  /** CQL template — $ref is replaced with the selected element */
  template: string
  /** Auto-suffix for the new definition name */
  nameSuffix: string
  color: string
}

const MODIFIER_PRESETS: ModifierPreset[] = [
  // General
  { labelKey: 'none',        template: '$ref',                                                                  nameSuffix: '',          color: '#546E7A' },
  { labelKey: 'exists',      template: '$ref is not null',                                                      nameSuffix: ' Exists',   color: '#1565C0' },
  { labelKey: 'existsList',  template: 'exists ($ref)',                                                         nameSuffix: ' Exists',   color: '#1565C0' },
  { labelKey: 'count',       template: 'Count($ref)',                                                           nameSuffix: ' Count',    color: '#7B1FA2' },
  { labelKey: 'first',       template: 'First($ref)',                                                           nameSuffix: ' First',    color: '#0D7377' },
  { labelKey: 'boolNot',     template: 'not ($ref)',                                                            nameSuffix: ' Not',      color: '#C62828' },
  // Observation → value extraction
  { labelKey: 'obsNumeric',  template: 'FHIRHelpers.ToDecimal(($ref.value as FHIR.Quantity).value)',            nameSuffix: ' Value',    color: '#9C27B0' },
  { labelKey: 'obsQuantity', template: 'FHIRHelpers.ToQuantity($ref.value as FHIR.Quantity)',                   nameSuffix: ' Quantity', color: '#E65100' },
  { labelKey: 'obsConcept',  template: 'FHIRHelpers.ToConcept($ref.value as FHIR.CodeableConcept)',            nameSuffix: ' Concept',  color: '#00695C' },
  { labelKey: 'obsRecent',   template: 'Last($ref O sort by FHIRHelpers.ToDateTime(effective as FHIR.dateTime))', nameSuffix: ' Recent', color: '#0D7377' },
]

export default function ElementRefBuilder({
  expressions,
  parameters,
  functions = [],
  onInsert,
  onCancel,
}: ElementRefBuilderProps) {
  const { t } = useTranslation('builder')
  const [search, setSearch] = useState('')
  const [selectedElement, setSelectedElement] = useState('')
  const [newName, setNewName] = useState('')
  const [extraExpr, setExtraExpr] = useState('')
  const [selectedPreset, setSelectedPreset] = useState<number | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const allOptions = useMemo(() => {
    const opts: ElementOption[] = []
    for (const expr of expressions) {
      opts.push({ value: `"${expr.name}"`, label: expr.name, resultType: expr.resultType, category: 'definition' })
    }
    for (const p of parameters) {
      const name = p.replace(/^"(.*)"$/, '$1')
      opts.push({ value: `"${name}"`, label: name, resultType: 'Parameter', category: 'parameter' })
    }
    for (const f of functions) {
      opts.push({ value: `"${f.name}"()`, label: `${f.name}()`, resultType: f.resultType, category: 'function' })
    }
    return opts
  }, [expressions, parameters, functions])

  const filteredOptions = useMemo(() => {
    if (!search) return allOptions
    const q = search.toLowerCase()
    return allOptions.filter((o) => o.label.toLowerCase().includes(q))
  }, [allOptions, search])

  const grouped = useMemo(() => {
    const map = new Map<string, ElementOption[]>()
    for (const opt of filteredOptions) {
      const list = map.get(opt.category) || []
      list.push(opt)
      map.set(opt.category, list)
    }
    return map
  }, [filteredOptions])

  const getBaseName = () => allOptions.find((o) => o.value === selectedElement)?.label || ''

  const handleSelect = (value: string) => {
    setSelectedElement(value)
    const label = allOptions.find((o) => o.value === value)?.label || ''
    const suffix = selectedPreset !== null ? MODIFIER_PRESETS[selectedPreset].nameSuffix : ''
    if (!newName || newName.endsWith(' Ref') || MODIFIER_PRESETS.some((p) => newName.endsWith(p.nameSuffix.trim()))) {
      setNewName(label ? `${label}${suffix || ' Ref'}` : '')
    }
    setPreviewSnippet('')
  }

  const handlePresetClick = (idx: number) => {
    const preset = MODIFIER_PRESETS[idx]
    if (selectedPreset === idx) {
      // Deselect
      setSelectedPreset(null)
      setExtraExpr('')
      if (selectedElement) {
        const base = getBaseName()
        setNewName(base ? `${base} Ref` : newName)
      }
    } else {
      setSelectedPreset(idx)
      setExtraExpr(preset.template)
      if (selectedElement) {
        const base = getBaseName()
        setNewName(base ? `${base}${preset.nameSuffix}` : newName)
      }
    }
    setPreviewSnippet('')
  }

  const handlePreview = () => {
    if (!newName.trim() || !selectedElement) return
    const body = extraExpr.trim()
      ? extraExpr.replace(/\$ref/g, selectedElement)
      : selectedElement
    setPreviewSnippet(`define "${newName}":\n  ${body}`)
  }

  const handleInsert = () => {
    onInsert(previewSnippet)
  }

  const isValid = newName.trim() && selectedElement

  const categoryLabels: Record<string, string> = {
    definition: t('elementRef.definitions'),
    parameter: t('elementRef.parameters'),
    function: t('elementRef.functions'),
  }

  return (
    <Stack spacing={1}>
      <Typography variant="caption" fontWeight={600} color="text.secondary">
        {t('elementRef.selectElement')}
      </Typography>

      {allOptions.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          {t('elementRef.noElements')}
        </Typography>
      ) : (
        <>
          <TextField
            select
            size="small"
            label={t('elementRef.selectElement')}
            value={selectedElement}
            onChange={(e) => handleSelect(e.target.value)}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            {/* Search */}
            <ListSubheader sx={{ p: 0.5 }}>
              <TextField
                size="small"
                fullWidth
                placeholder={t('elementRef.searchPlaceholder')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                onKeyDown={(e) => e.stopPropagation()}
                InputProps={{
                  startAdornment: (
                    <InputAdornment position="start">
                      <SearchIcon sx={{ fontSize: 16 }} />
                    </InputAdornment>
                  ),
                }}
                sx={{ '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
              />
            </ListSubheader>

            <MenuItem value="" disabled>
              <em>{t('elementRef.selectElement')}</em>
            </MenuItem>

            {(['definition', 'parameter', 'function'] as const).map((cat) => {
              const items = grouped.get(cat)
              if (!items || items.length === 0) return null
              return [
                <ListSubheader
                  key={`header-${cat}`}
                  sx={{ fontSize: '0.7rem', fontWeight: 700, lineHeight: '28px', bgcolor: 'background.paper', color: 'text.secondary' }}
                >
                  {categoryLabels[cat]}
                </ListSubheader>,
                ...items.map((opt) => (
                  <MenuItem key={opt.value} value={opt.value} sx={{ fontSize: '0.8rem', py: 0.5, gap: 1 }}>
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.75rem', flex: 1 }}>
                      {opt.label}
                    </Typography>
                    <TypeChip resultType={opt.resultType} />
                  </MenuItem>
                )),
              ]
            })}
          </TextField>

          {/* Modifier presets */}
          <Box>
            <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
              {t('elementRef.modifier')}
            </Typography>
            <Stack direction="row" flexWrap="wrap" gap={0.5}>
              {MODIFIER_PRESETS.map((preset, idx) => (
                <Chip
                  key={idx}
                  label={t(`elementRef.presets.${preset.labelKey}`)}
                  size="small"
                  onClick={() => handlePresetClick(idx)}
                  variant={selectedPreset === idx ? 'filled' : 'outlined'}
                  sx={{
                    height: 24,
                    fontSize: '0.68rem',
                    fontWeight: 600,
                    cursor: 'pointer',
                    color: selectedPreset === idx ? '#fff' : preset.color,
                    bgcolor: selectedPreset === idx ? preset.color : 'transparent',
                    borderColor: preset.color,
                    '&:hover': { bgcolor: selectedPreset === idx ? preset.color : `${preset.color}14` },
                  }}
                />
              ))}
            </Stack>
          </Box>

          <TextField
            size="small"
            label={t('elementRef.newName')}
            value={newName}
            onChange={(e) => { setNewName(e.target.value); setPreviewSnippet('') }}
          />

          <TextField
            size="small"
            label={t('elementRef.expression')}
            value={extraExpr}
            onChange={(e) => { setExtraExpr(e.target.value); setSelectedPreset(null); setPreviewSnippet('') }}
            placeholder="e.g. $ref.value, exists $ref, Count($ref)"
            helperText={t('elementRef.simpleRef')}
            sx={{ '& input': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
          />
        </>
      )}

      {previewSnippet ? (
        <SnippetPreview
          snippet={previewSnippet}
          onInsert={handleInsert}
          onCancel={() => setPreviewSnippet('')}
        />
      ) : (
        <Stack direction="row" spacing={1}>
          <Button size="small" variant="outlined" onClick={handlePreview} disabled={!isValid}>
            {t('common.previewInsert')}
          </Button>
          <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
        </Stack>
      )}
    </Stack>
  )
}
