import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import UcumUnitField from '../common/UcumUnitField'

interface ParametersSectionProps {
  parameters: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

const CQL_TYPES = [
  'Boolean',
  'Integer',
  'Decimal',
  'String',
  'DateTime',
  'Date',
  'Time',
  'Quantity',
  'Code',
  'Concept',
  'Interval<DateTime>',
  'Interval<Date>',
  'Interval<Integer>',
  'Interval<Decimal>',
  'Interval<Quantity>',
  'List<String>',
  'List<Code>',
]

const INTERVAL_DATE_RE = /^Interval\[@([^,]+),\s*@([^\]]+)\]$/
const INTERVAL_NUM_RE = /^Interval\[([^,]+),\s*([^\]]+)\]$/

/**
 * Parse a parameter string like: "Measurement Period" Interval<DateTime>
 *   default Interval[@2024-01-01T00:00:00.000, @2024-12-31T23:59:59.999]
 */
function parseParameter(raw: string): { name: string; type: string; defaultValue: string } | null {
  const m = raw.match(/^"([^"]+)"\s+(\S+)(?:\s+default\s+(.+))?/)
  if (m) return { name: m[1], type: m[2], defaultValue: m[3] || '' }
  return null
}

function formatDateTimeForCql(dateStr: string, isEnd: boolean): string {
  if (!dateStr) return ''
  // CQL DateTime literals require either no fractional part or a 3-digit
  // fraction. Use `.000` / `.999` so start and end have matching precision.
  // Input from date input: YYYY-MM-DD, from datetime-local: YYYY-MM-DDTHH:mm
  if (dateStr.includes('T')) return dateStr + ':00.000'
  return isEnd ? dateStr + 'T23:59:59.999' : dateStr + 'T00:00:00.000'
}

function parseDateTimeFromCql(cqlDate: string): string {
  if (!cqlDate) return ''
  // Strip trailing seconds/ms for datetime-local input: YYYY-MM-DDTHH:mm:ss.s → YYYY-MM-DDTHH:mm
  const m = cqlDate.match(/^(\d{4}-\d{2}-\d{2})(T(\d{2}:\d{2}))?/)
  if (m) return m[3] ? `${m[1]}T${m[3]}` : m[1]
  return cqlDate
}

export default function ParametersSection({ parameters, onInsert, onDelete, onGoTo, onEdit }: ParametersSectionProps) {
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [paramType, setParamType] = useState('Boolean')
  const [defaultValue, setDefaultValue] = useState('')

  // Quantity-specific state for structured input
  const [qtyValue, setQtyValue] = useState('')
  const [qtyUnit, setQtyUnit] = useState('')

  // Interval-specific state for structured input
  const [intervalStart, setIntervalStart] = useState('')
  const [intervalEnd, setIntervalEnd] = useState('')
  // Raw CQL of the original interval default while editing — datetime-local
  // inputs only carry minute precision, so naive round-trips drop seconds/ms.
  // We reuse the original when the user hasn't touched the inputs.
  const [originalIntervalDefault, setOriginalIntervalDefault] = useState('')

  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const isQuantityType = paramType === 'Quantity' || paramType === 'Interval<Quantity>'
  const isIntervalDateType = paramType === 'Interval<DateTime>' || paramType === 'Interval<Date>'
  const isIntervalNumType = paramType === 'Interval<Integer>' || paramType === 'Interval<Decimal>'

  const buildIntervalDefault = (): string => {
    // Editing path: reuse original raw default if user hasn't edited the
    // structured inputs. Preserves seconds/ms precision that the
    // datetime-local input would otherwise truncate.
    if (originalIntervalDefault) return originalIntervalDefault
    if (isIntervalDateType) {
      if (!intervalStart && !intervalEnd) return ''
      const isDateTime = paramType === 'Interval<DateTime>'
      const start = isDateTime
        ? formatDateTimeForCql(intervalStart, false)
        : intervalStart
      const end = isDateTime
        ? formatDateTimeForCql(intervalEnd, true)
        : intervalEnd
      if (!start || !end) return ''
      return `Interval[@${start}, @${end}]`
    }
    if (isIntervalNumType) {
      if (!intervalStart.trim() || !intervalEnd.trim()) return ''
      return `Interval[${intervalStart.trim()}, ${intervalEnd.trim()}]`
    }
    return ''
  }

  const handleAdd = () => {
    if (!name.trim()) return
    let snippet = `parameter "${name}" ${paramType}`
    if (isQuantityType) {
      if (qtyValue.trim() && qtyUnit.trim()) {
        snippet += `\n  default ${qtyValue.trim()} '${qtyUnit.trim()}'`
      }
    } else if (isIntervalDateType || isIntervalNumType) {
      const intervalDefault = buildIntervalDefault()
      if (intervalDefault) {
        snippet += `\n  default ${intervalDefault}`
      }
    } else if (defaultValue.trim()) {
      snippet += `\n  default ${defaultValue}`
    }
    setPreviewSnippet(snippet)
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (raw: string) => {
    const parsed = parseParameter(raw)
    if (!parsed) return
    setEditingItem(parsed.name)
    setName(parsed.name)
    const type = CQL_TYPES.includes(parsed.type) ? parsed.type : 'Boolean'
    setParamType(type)

    // Reset all structured fields
    setQtyValue('')
    setQtyUnit('')
    setIntervalStart('')
    setIntervalEnd('')
    setDefaultValue('')

    if ((type === 'Quantity' || type === 'Interval<Quantity>') && parsed.defaultValue) {
      const qm = parsed.defaultValue.match(/^(\S+)\s+'([^']*)'$/)
      if (qm) {
        setQtyValue(qm[1])
        setQtyUnit(qm[2])
      } else {
        setDefaultValue(parsed.defaultValue)
      }
    } else if ((type === 'Interval<DateTime>' || type === 'Interval<Date>') && parsed.defaultValue) {
      const dm = parsed.defaultValue.match(INTERVAL_DATE_RE)
      if (dm) {
        setIntervalStart(parseDateTimeFromCql(dm[1]))
        setIntervalEnd(parseDateTimeFromCql(dm[2]))
        setOriginalIntervalDefault(parsed.defaultValue)
      } else {
        setDefaultValue(parsed.defaultValue)
      }
    } else if ((type === 'Interval<Integer>' || type === 'Interval<Decimal>') && parsed.defaultValue) {
      const nm = parsed.defaultValue.match(INTERVAL_NUM_RE)
      if (nm) {
        setIntervalStart(nm[1].trim())
        setIntervalEnd(nm[2].trim())
        setOriginalIntervalDefault(parsed.defaultValue)
      } else {
        setDefaultValue(parsed.defaultValue)
      }
    } else {
      setDefaultValue(parsed.defaultValue)
    }
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setParamType('Boolean')
    setDefaultValue('')
    setQtyValue('')
    setQtyUnit('')
    setIntervalStart('')
    setIntervalEnd('')
    setOriginalIntervalDefault('')
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  const renderDefaultValueInput = () => {
    if (isQuantityType) {
      return (
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            label={t('parameters.defaultValue')}
            type="number"
            value={qtyValue}
            onChange={(e) => setQtyValue(e.target.value)}
            placeholder="e.g. 70"
            sx={{ flex: 1 }}
          />
          <UcumUnitField
            label="Unit"
            value={qtyUnit}
            onChange={setQtyUnit}
            sx={{ flex: 1 }}
          />
        </Stack>
      )
    }

    // Editing an interval and changing either bound discards the original raw
    // default so we re-emit from the current input values.
    const handleIntervalStart = (v: string) => {
      setIntervalStart(v)
      setOriginalIntervalDefault('')
    }
    const handleIntervalEnd = (v: string) => {
      setIntervalEnd(v)
      setOriginalIntervalDefault('')
    }

    if (isIntervalDateType) {
      const inputType = paramType === 'Interval<DateTime>' ? 'datetime-local' : 'date'
      return (
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            type={inputType}
            label={t('parameters.intervalStart')}
            value={intervalStart}
            onChange={(e) => handleIntervalStart(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ flex: 1 }}
          />
          <TextField
            size="small"
            type={inputType}
            label={t('parameters.intervalEnd')}
            value={intervalEnd}
            onChange={(e) => handleIntervalEnd(e.target.value)}
            InputLabelProps={{ shrink: true }}
            sx={{ flex: 1 }}
          />
        </Stack>
      )
    }

    if (isIntervalNumType) {
      return (
        <Stack direction="row" spacing={1}>
          <TextField
            size="small"
            type="number"
            label={t('parameters.intervalStart')}
            value={intervalStart}
            onChange={(e) => handleIntervalStart(e.target.value)}
            placeholder={t('parameters.intervalStartPlaceholder')}
            sx={{ flex: 1 }}
          />
          <TextField
            size="small"
            type="number"
            label={t('parameters.intervalEnd')}
            value={intervalEnd}
            onChange={(e) => handleIntervalEnd(e.target.value)}
            placeholder={t('parameters.intervalEndPlaceholder')}
            sx={{ flex: 1 }}
          />
        </Stack>
      )
    }

    return (
      <TextField
        size="small"
        label={t('parameters.defaultValue')}
        value={defaultValue}
        onChange={(e) => setDefaultValue(e.target.value)}
        placeholder={t('parameters.defaultPlaceholder')}
      />
    )
  }

  return (
    <Stack spacing={0.5}>
      {parameters.length > 0 ? (
        parameters.map((param, idx) => {
          const parsed = parseParameter(param)
          const name = parsed?.name || param
          return (
            <ElementListItem
              key={idx}
              label={name}
              secondaryLabel={parsed?.type}
              onGoTo={() => onGoTo?.(name)}
              onEdit={() => handleStartEdit(param)}
              onDelete={() => setDeleteTarget(name)}
            />
          )
        })
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          {t('common.noItemsFound', { type: t('sections.parameters').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Parameter' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.03), borderRadius: 1 }}>
          <TextField
            size="small"
            label={t('parameters.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <TextField
            select
            size="small"
            label={t('parameters.type')}
            value={paramType}
            onChange={(e) => setParamType(e.target.value)}
          >
            {CQL_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>

          {renderDefaultValueInput()}

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? t('common.update') : t('common.insert')}
            />
          ) : (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd} disabled={!name.trim()}>
                {editingItem ? t('common.previewUpdate') : t('common.previewInsert')}
              </Button>
              <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
            </Stack>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title={t('common.deleteElement')}
        itemName={deleteTarget || ''}
        message={t('common.deleteConfirm', { name: deleteTarget })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
