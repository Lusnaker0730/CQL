import { useState, useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  Button,
  IconButton,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
  Paper,
  Divider,
  Chip,
  Switch,
  FormControlLabel,
} from '@mui/material'
import {
  ContentCopy as CopyIcon,
  Add as AddIcon,
  Close as CloseIcon,
} from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'
import { escapeCqlString, formatFieldValue } from '../../utils/cqlString'
import type { FieldState } from '../../utils/cqlString'

interface RecommendationBuilderProps {
  expressions: string[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

interface LinkItem {
  id: number
  type: 'absolute' | 'smart'
  label: string
  url: string
}

interface ActionItem {
  id: number
  type: string
  description: string
  resourceType: string
}

interface SuggestionItem {
  id: number
  label: string
  isRecommended: boolean
  actions: ActionItem[]
}

const INDICATORS = ['info', 'warning', 'critical'] as const
const GRADES = ['', 'A', 'B', 'C', 'D', 'I'] as const

let nextId = 1

export default function RecommendationBuilder({ expressions, onInsert, onCancel }: RecommendationBuilderProps) {
  const { t } = useTranslation('builder')
  const copyToClipboard = useCopyToClipboard()
  const [name, setName] = useState('Card')
  const [summary, setSummary] = useState<FieldState>({ value: '', mode: 'literal' })
  const [detail, setDetail] = useState<FieldState>({ value: '', mode: 'literal' })
  const [indicator, setIndicator] = useState<string>('info')
  const [sourceLabel, setSourceLabel] = useState<FieldState>({ value: '', mode: 'literal' })
  const [grade, setGrade] = useState('')
  const [rationale, setRationale] = useState('')
  const [selectionBehavior, setSelectionBehavior] = useState('')
  const [condition, setCondition] = useState('')
  const [links, setLinks] = useState<LinkItem[]>([])
  const [suggestions, setSuggestions] = useState<SuggestionItem[]>([])

  const addLink = () => setLinks((prev) => [...prev, { id: nextId++, type: 'absolute', label: '', url: '' }])
  const removeLink = (id: number) => setLinks((prev) => prev.filter((l) => l.id !== id))
  const updateLink = (id: number, field: keyof LinkItem, value: string) =>
    setLinks((prev) => prev.map((l) => l.id === id ? { ...l, [field]: value } : l))

  const addSuggestion = () =>
    setSuggestions((prev) => [...prev, { id: nextId++, label: '', isRecommended: false, actions: [] }])
  const removeSuggestion = (id: number) => setSuggestions((prev) => prev.filter((s) => s.id !== id))
  const updateSuggestion = (id: number, field: string, value: unknown) =>
    setSuggestions((prev) => prev.map((s) => s.id === id ? { ...s, [field]: value } : s))

  const addAction = (sugId: number) =>
    setSuggestions((prev) => prev.map((s) =>
      s.id === sugId ? { ...s, actions: [...s.actions, { id: nextId++, type: 'create', description: '', resourceType: 'MedicationRequest' }] } : s
    ))
  const removeAction = (sugId: number, actionId: number) =>
    setSuggestions((prev) => prev.map((s) =>
      s.id === sugId ? { ...s, actions: s.actions.filter((a) => a.id !== actionId) } : s
    ))
  const updateAction = (sugId: number, actionId: number, field: string, value: string) =>
    setSuggestions((prev) => prev.map((s) =>
      s.id === sugId ? { ...s, actions: s.actions.map((a) => a.id === actionId ? { ...a, [field]: value } : a) } : s
    ))

  const cqlPreview = useMemo(() => {
    if (!name.trim() || !summary.value.trim()) return ''

    const fields: string[] = []
    fields.push(`    summary: ${formatFieldValue(summary)}`)
    if (detail.value.trim()) {
      fields.push(`    detail: ${formatFieldValue(detail)}`)
    }
    fields.push(`    indicator: '${indicator}'`)
    if (sourceLabel.value.trim()) {
      fields.push(`    sourceLabel: ${formatFieldValue(sourceLabel)}`)
    }
    if (grade) {
      fields.push(`    grade: '${grade}'`)
    }
    if (rationale.trim()) {
      fields.push(`    rationale: '${escapeCqlString(rationale)}'`)
    }
    if (selectionBehavior) {
      fields.push(`    selectionBehavior: '${selectionBehavior}'`)
    }

    // Links
    if (links.some((l) => l.label && l.url)) {
      const linkEntries = links
        .filter((l) => l.label && l.url)
        .map((l) => `      Tuple { type: '${l.type}', label: '${escapeCqlString(l.label)}', url: '${escapeCqlString(l.url)}' }`)
      fields.push(`    links: List {\n${linkEntries.join(',\n')}\n    }`)
    }

    // Suggestions
    if (suggestions.some((s) => s.label)) {
      const sugEntries = suggestions
        .filter((s) => s.label)
        .map((s) => {
          const sugFields: string[] = []
          sugFields.push(`        label: '${escapeCqlString(s.label)}'`)
          sugFields.push(`        isRecommended: ${s.isRecommended}`)
          if (s.actions.some((a) => a.description)) {
            const actionEntries = s.actions
              .filter((a) => a.description)
              .map((a) => `          Tuple { type: '${a.type}', description: '${escapeCqlString(a.description)}', resourceType: '${a.resourceType}' }`)
            sugFields.push(`        actions: List {\n${actionEntries.join(',\n')}\n        }`)
          }
          return `      Tuple {\n${sugFields.join(',\n')}\n      }`
        })
      fields.push(`    suggestions: List {\n${sugEntries.join(',\n')}\n    }`)
    }

    const tupleBody = `Tuple {\n${fields.join(',\n')}\n  }`

    if (condition) {
      return `define "${name}":\n  if ${condition} then\n    ${tupleBody.replace(/\n/g, '\n    ')}\n  else\n    null`
    }
    return `define "${name}":\n  ${tupleBody}`
  }, [name, summary, detail, indicator, sourceLabel, grade, rationale, selectionBehavior, condition, links, suggestions])

  const handleInsert = () => {
    if (!cqlPreview) return
    onInsert(cqlPreview)
  }

  const toggleSx = { textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem', minWidth: 0 } as const

  const renderFieldWithMode = (
    label: string,
    labelExpr: string,
    field: FieldState,
    setField: (fn: (prev: FieldState) => FieldState) => void,
    multiline = false,
  ) => (
    <Stack spacing={0.5}>
      <Stack direction="row" alignItems="center" spacing={1}>
        <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
          {label}
        </Typography>
        <ToggleButtonGroup
          size="small"
          exclusive
          value={field.mode}
          onChange={(_, v) => { if (v) setField((s) => ({ ...s, mode: v })) }}
        >
          <ToggleButton value="literal" sx={toggleSx}>{t('recommendation.literal')}</ToggleButton>
          <ToggleButton value="expression" sx={toggleSx}>{t('recommendation.expr')}</ToggleButton>
        </ToggleButtonGroup>
      </Stack>
      {field.mode === 'literal' ? (
        <TextField
          size="small"
          label={label}
          multiline={multiline}
          rows={multiline ? 2 : undefined}
          value={field.value}
          onChange={(e) => setField((s) => ({ ...s, value: e.target.value }))}
        />
      ) : (
        <TextField
          select
          size="small"
          label={labelExpr}
          value={field.value}
          onChange={(e) => setField((s) => ({ ...s, value: e.target.value }))}
          SelectProps={{ displayEmpty: true }}
          InputLabelProps={{ shrink: true }}
        >
          <MenuItem value="" disabled><em>{t('recommendation.selectDefinition')}</em></MenuItem>
          {expressions.map((expr) => (
            <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
          ))}
        </TextField>
      )}
    </Stack>
  )

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        label={t('definitions.name')}
        value={name}
        onChange={(e) => setName(e.target.value)}
        helperText={t('recommendation.nameHint')}
      />

      {/* Condition — select an existing definition as trigger */}
      <TextField
        select
        size="small"
        label={t('recommendation.condition')}
        value={condition}
        onChange={(e) => setCondition(e.target.value)}
        SelectProps={{ displayEmpty: true }}
        InputLabelProps={{ shrink: true }}
        helperText={t('recommendation.conditionHint')}
      >
        <MenuItem value="">{t('recommendation.conditionNone')}</MenuItem>
        {expressions.map((expr) => (
          <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
        ))}
      </TextField>

      {/* Summary */}
      {renderFieldWithMode(t('recommendation.summary'), t('recommendation.summaryExpr'), summary, setSummary)}

      {/* Detail */}
      {renderFieldWithMode(t('recommendation.detail'), t('recommendation.detailExpr'), detail, setDetail, true)}

      {/* Indicator + Grade row */}
      <Stack direction="row" spacing={1}>
        <TextField
          select
          size="small"
          label={t('recommendation.indicator')}
          value={indicator}
          onChange={(e) => setIndicator(e.target.value)}
          sx={{ flex: 1 }}
        >
          {INDICATORS.map((ind) => (
            <MenuItem key={ind} value={ind}>{ind}</MenuItem>
          ))}
        </TextField>

        <TextField
          select
          size="small"
          label={t('recommendation.grade')}
          value={grade}
          onChange={(e) => setGrade(e.target.value)}
          sx={{ flex: 1 }}
        >
          {GRADES.map((g) => (
            <MenuItem key={g || '_none'} value={g}>
              {g || t('recommendation.gradeNone')}
            </MenuItem>
          ))}
        </TextField>
      </Stack>

      {/* Source Label */}
      {renderFieldWithMode(t('recommendation.sourceLabel'), t('recommendation.sourceLabelExpr'), sourceLabel, setSourceLabel)}

      {/* Rationale */}
      <TextField
        size="small"
        label={t('recommendation.rationale')}
        multiline
        rows={2}
        value={rationale}
        onChange={(e) => setRationale(e.target.value)}
        placeholder={t('recommendation.rationalePlaceholder')}
      />

      {/* Selection Behavior */}
      <TextField
        select
        size="small"
        label={t('recommendation.selectionBehavior')}
        value={selectionBehavior}
        onChange={(e) => setSelectionBehavior(e.target.value)}
      >
        <MenuItem value="">{t('recommendation.selectionNone')}</MenuItem>
        <MenuItem value="at-most-one">{t('recommendation.selectionAtMostOne')}</MenuItem>
        <MenuItem value="any">{t('recommendation.selectionAny')}</MenuItem>
      </TextField>

      <Divider />

      {/* Links */}
      <Stack spacing={0.5}>
        <Typography variant="caption" fontWeight={600} color="text.secondary">
          {t('recommendation.links')}
        </Typography>
        {links.map((link) => (
          <Paper key={link.id} variant="outlined" sx={{ p: 0.75 }}>
            <Stack direction="row" spacing={0.5} alignItems="center">
              <TextField
                select
                size="small"
                label={t('recommendation.linkType')}
                value={link.type}
                onChange={(e) => updateLink(link.id, 'type', e.target.value as 'absolute' | 'smart')}
                sx={{ width: 100, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
              >
                <MenuItem value="absolute">{t('recommendation.linkAbsolute')}</MenuItem>
                <MenuItem value="smart">{t('recommendation.linkSmart')}</MenuItem>
              </TextField>
              <TextField
                size="small"
                label={t('recommendation.linkLabel')}
                value={link.label}
                onChange={(e) => updateLink(link.id, 'label', e.target.value)}
                sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
              />
              <TextField
                size="small"
                label={t('recommendation.linkUrl')}
                value={link.url}
                onChange={(e) => updateLink(link.id, 'url', e.target.value)}
                sx={{ flex: 2, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
              />
              <IconButton size="small" onClick={() => removeLink(link.id)} sx={{ color: 'error.main' }}>
                <CloseIcon sx={{ fontSize: 14 }} />
              </IconButton>
            </Stack>
          </Paper>
        ))}
        <Button size="small" startIcon={<AddIcon sx={{ fontSize: 14 }} />} onClick={addLink} sx={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
          {t('recommendation.addLink')}
        </Button>
      </Stack>

      <Divider />

      {/* Suggestions */}
      <Stack spacing={0.5}>
        <Typography variant="caption" fontWeight={600} color="text.secondary">
          {t('recommendation.suggestions')}
        </Typography>
        {suggestions.map((sug) => (
          <Paper key={sug.id} variant="outlined" sx={{ p: 0.75 }}>
            <Stack spacing={0.5}>
              <Stack direction="row" spacing={0.5} alignItems="center">
                <TextField
                  size="small"
                  label={t('recommendation.suggestionLabel')}
                  value={sug.label}
                  onChange={(e) => updateSuggestion(sug.id, 'label', e.target.value)}
                  sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                />
                <FormControlLabel
                  control={
                    <Switch
                      size="small"
                      checked={sug.isRecommended}
                      onChange={(e) => updateSuggestion(sug.id, 'isRecommended', e.target.checked)}
                    />
                  }
                  label={<Typography variant="caption">{t('recommendation.isRecommended')}</Typography>}
                />
                <IconButton size="small" onClick={() => removeSuggestion(sug.id)} sx={{ color: 'error.main' }}>
                  <CloseIcon sx={{ fontSize: 14 }} />
                </IconButton>
              </Stack>

              {/* Actions within suggestion */}
              <Stack spacing={0.5} sx={{ pl: 1.5 }}>
                <Typography variant="caption" color="text.secondary">{t('recommendation.actions')}</Typography>
                {sug.actions.map((act) => (
                  <Stack key={act.id} direction="row" spacing={0.5} alignItems="center">
                    <Chip label={act.type} size="small" sx={{ fontSize: '0.65rem' }} />
                    <TextField
                      size="small"
                      label={t('recommendation.actionDescription')}
                      value={act.description}
                      onChange={(e) => updateAction(sug.id, act.id, 'description', e.target.value)}
                      sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                    />
                    <TextField
                      select
                      size="small"
                      label={t('recommendation.actionResourceType')}
                      value={act.resourceType}
                      onChange={(e) => updateAction(sug.id, act.id, 'resourceType', e.target.value)}
                      sx={{ width: 160, '& .MuiInputBase-input': { fontSize: '0.75rem' } }}
                    >
                      <MenuItem value="MedicationRequest">MedicationRequest</MenuItem>
                      <MenuItem value="ServiceRequest">ServiceRequest</MenuItem>
                    </TextField>
                    <IconButton size="small" onClick={() => removeAction(sug.id, act.id)} sx={{ color: 'error.main' }}>
                      <CloseIcon sx={{ fontSize: 14 }} />
                    </IconButton>
                  </Stack>
                ))}
                <Button size="small" startIcon={<AddIcon sx={{ fontSize: 14 }} />} onClick={() => addAction(sug.id)} sx={{ alignSelf: 'flex-start', fontSize: '0.65rem' }}>
                  {t('recommendation.addAction')}
                </Button>
              </Stack>
            </Stack>
          </Paper>
        ))}
        <Button size="small" startIcon={<AddIcon sx={{ fontSize: 14 }} />} onClick={addSuggestion} sx={{ alignSelf: 'flex-start', fontSize: '0.7rem' }}>
          {t('recommendation.addSuggestion')}
        </Button>
      </Stack>

      {/* CQL Preview */}
      <CqlPreviewBox code={cqlPreview} />

      {/* Action buttons */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton onClick={handleInsert} disabled={!cqlPreview}>
          {t('common.insert')}
        </GradientButton>
        {cqlPreview && (
          <Tooltip title={t('common.copyToClipboard')}>
            <IconButton size="small" onClick={() => copyToClipboard(cqlPreview)}>
              <CopyIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
      </Stack>
    </Stack>
  )
}
