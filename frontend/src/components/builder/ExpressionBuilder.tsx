import { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  IconButton,
  Box,
  ToggleButtonGroup,
  ToggleButton,
  Tooltip,
} from '@mui/material'
import {
  Add as AddIcon,
  Delete as DeleteIcon,
} from '@mui/icons-material'
import SnippetPreview from './SnippetPreview'

interface ExpressionRow {
  id: string
  operand: string
  operator: string
  value: string
  conjunction: 'and' | 'or'
}

interface ExpressionBuilderProps {
  expressions: string[]
  parameters: string[]
  valueSets: string[]
  onInsert: (snippet: string) => void
  onCancel: () => void
}

const COMPARISON_OPS = ['=', '!=', '~', '!~', '>', '<', '>=', '<=']
const TEMPORAL_OPS = ['before', 'after', 'during', 'overlaps', 'within', 'meets', 'starts', 'ends']
const TYPE_OPS = ['is null', 'is not null', 'is', 'as']
const UNARY_OPS = ['is null', 'is not null']

const ALL_OPERATORS = [
  { group: 'Comparison', ops: COMPARISON_OPS },
  { group: 'Temporal', ops: TEMPORAL_OPS },
  { group: 'Type', ops: TYPE_OPS },
]

const WRAPPING_FUNCTIONS = [
  { value: '', label: 'None' },
  { value: 'exists', label: 'exists(...)' },
  { value: 'not', label: 'not(...)' },
  { value: 'Count', label: 'Count(...)' },
  { value: 'Last', label: 'Last(...)' },
  { value: 'First', label: 'First(...)' },
]

let rowIdCounter = 0
function nextId() { return `row-${++rowIdCounter}` }

export default function ExpressionBuilder({
  expressions,
  parameters,
  valueSets,
  onInsert,
  onCancel,
}: ExpressionBuilderProps) {
  const { t } = useTranslation('builder')
  const [name, setName] = useState('')
  const [rows, setRows] = useState<ExpressionRow[]>([
    { id: nextId(), operand: '', operator: '', value: '', conjunction: 'and' },
  ])
  const [wrapping, setWrapping] = useState('')
  const [previewSnippet, setPreviewSnippet] = useState('')

  const operandOptions = useMemo(() => {
    const opts: { value: string; label: string; group: string }[] = []
    for (const e of expressions) {
      opts.push({ value: `"${e}"`, label: e, group: 'Definitions' })
    }
    for (const p of parameters) {
      const m = p.match(/^"([^"]+)"/)
      if (m) opts.push({ value: `"${m[1]}"`, label: m[1], group: 'Parameters' })
    }
    for (const vs of valueSets) {
      const m = vs.match(/^"([^"]+)"/)
      if (m) opts.push({ value: `"${m[1]}"`, label: m[1], group: 'Value Sets' })
    }
    // Built-in functions
    opts.push({ value: 'AgeInYearsAt(start of "Measurement Period")', label: 'AgeInYearsAt(...)', group: 'Functions' })
    opts.push({ value: 'AgeInYears()', label: 'AgeInYears()', group: 'Functions' })
    opts.push({ value: 'AgeInMonths()', label: 'AgeInMonths()', group: 'Functions' })
    opts.push({ value: 'Today()', label: 'Today()', group: 'Functions' })
    opts.push({ value: 'Now()', label: 'Now()', group: 'Functions' })
    opts.push({ value: 'Count()', label: 'Count(...)', group: 'Functions' })
    return opts
  }, [expressions, parameters, valueSets])

  const updateRow = useCallback((id: string, field: keyof ExpressionRow, value: string) => {
    setRows((prev) => prev.map((r) => r.id === id ? { ...r, [field]: value } : r))
    setPreviewSnippet('')
  }, [])

  const addRow = () => {
    setRows((prev) => [...prev, { id: nextId(), operand: '', operator: '', value: '', conjunction: 'and' }])
    setPreviewSnippet('')
  }

  const removeRow = (id: string) => {
    setRows((prev) => prev.length > 1 ? prev.filter((r) => r.id !== id) : prev)
    setPreviewSnippet('')
  }

  const generateCql = (): string => {
    if (!name.trim()) return ''
    const parts: string[] = []
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i]
      if (!row.operand) continue

      let line = ''
      if (i > 0) {
        line = `${row.conjunction} `
      }

      if (UNARY_OPS.includes(row.operator)) {
        line += `${row.operand} ${row.operator}`
      } else if (row.operator && row.value) {
        line += `${row.operand} ${row.operator} ${row.value}`
      } else {
        line += row.operand
      }

      parts.push(line)
    }

    if (parts.length === 0) return ''

    let body = parts[0]
    for (let i = 1; i < parts.length; i++) {
      body += `\n    ${parts[i]}`
    }

    if (wrapping) {
      body = `${wrapping} (\n    ${parts.join('\n      ')}\n  )`
    }

    return `define "${name}":\n  ${body}`
  }

  const handlePreview = () => {
    const cql = generateCql()
    if (cql) setPreviewSnippet(cql)
  }

  const handleInsert = () => {
    onInsert(previewSnippet)
  }

  const isValid = name.trim() && rows.some((r) => r.operand)

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        label={t('definitions.name')}
        value={name}
        onChange={(e) => { setName(e.target.value); setPreviewSnippet('') }}
      />

      <Typography variant="caption" fontWeight={500} color="text.secondary">
        {t('expression.rows')}
      </Typography>

      {rows.map((row, idx) => (
        <Box
          key={row.id}
          sx={{
            p: 0.75,
            border: '1px solid',
            borderColor: 'divider',
            borderRadius: 1,
            bgcolor: 'rgba(27,58,92,0.02)',
          }}
        >
          {idx > 0 && (
            <ToggleButtonGroup
              size="small"
              exclusive
              value={row.conjunction}
              onChange={(_, v) => { if (v) updateRow(row.id, 'conjunction', v) }}
              sx={{ mb: 0.5 }}
            >
              <ToggleButton value="and" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.75rem' }}>
                and
              </ToggleButton>
              <ToggleButton value="or" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.75rem' }}>
                or
              </ToggleButton>
            </ToggleButtonGroup>
          )}

          <Stack spacing={0.5}>
            <TextField
              select
              size="small"
              label="Operand"
              value={row.operand}
              onChange={(e) => updateRow(row.id, 'operand', e.target.value)}
              sx={{ '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
            >
              <MenuItem value="">
                <em>{t('expression.select')}</em>
              </MenuItem>
              {operandOptions.map((opt) => (
                <MenuItem key={opt.value} value={opt.value} sx={{ fontSize: '0.8rem' }}>
                  {opt.label}
                </MenuItem>
              ))}
            </TextField>

            <Stack direction="row" spacing={0.5} alignItems="center">
              <TextField
                select
                size="small"
                label="Operator"
                value={row.operator}
                onChange={(e) => updateRow(row.id, 'operator', e.target.value)}
                sx={{ flex: 1, '& .MuiInputBase-input': { fontSize: '0.8rem' } }}
              >
                <MenuItem value="">
                  <em>{t('expression.none')}</em>
                </MenuItem>
                {ALL_OPERATORS.map((group) => [
                  <MenuItem key={`header-${group.group}`} disabled sx={{ fontSize: '0.7rem', fontWeight: 600, opacity: 1 }}>
                    {group.group}
                  </MenuItem>,
                  ...group.ops.map((op) => (
                    <MenuItem key={op} value={op} sx={{ fontSize: '0.8rem', pl: 3 }}>
                      {op}
                    </MenuItem>
                  )),
                ])}
              </TextField>

              {row.operator && !UNARY_OPS.includes(row.operator) && (
                <TextField
                  size="small"
                  label="Value"
                  value={row.value}
                  onChange={(e) => updateRow(row.id, 'value', e.target.value)}
                  sx={{ flex: 1, '& input': { fontSize: '0.8rem', fontFamily: 'monospace' } }}
                  placeholder={t('expression.valuePlaceholder')}
                />
              )}

              {rows.length > 1 && (
                <Tooltip title={t('expression.removeRow')}>
                  <IconButton size="small" onClick={() => removeRow(row.id)} sx={{ color: 'error.main' }} aria-label={t('expression.removeRow')}>
                    <DeleteIcon sx={{ fontSize: 16 }} />
                  </IconButton>
                </Tooltip>
              )}
            </Stack>
          </Stack>
        </Box>
      ))}

      <Button size="small" startIcon={<AddIcon />} onClick={addRow} sx={{ alignSelf: 'flex-start', fontSize: '0.75rem' }}>
        {t('expression.addRow')}
      </Button>

      <TextField
        select
        size="small"
        label={t('expression.wrappingFunction')}
        value={wrapping}
        onChange={(e) => { setWrapping(e.target.value); setPreviewSnippet('') }}
      >
        {WRAPPING_FUNCTIONS.map((wf) => (
          <MenuItem key={wf.value} value={wf.value}>{wf.label}</MenuItem>
        ))}
      </TextField>

      {previewSnippet ? (
        <SnippetPreview
          snippet={previewSnippet}
          onInsert={handleInsert}
          onCancel={() => setPreviewSnippet('')}
        />
      ) : (
        <Stack direction="row" spacing={1}>
          <Button size="small" variant="outlined" onClick={handlePreview} disabled={!isValid}>
            {t('expression.preview')}
          </Button>
          <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
        </Stack>
      )}
    </Stack>
  )
}
