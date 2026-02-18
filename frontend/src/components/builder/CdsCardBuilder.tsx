import { useState, useMemo } from 'react'
import {
  Stack,
  TextField,
  MenuItem,
  Typography,
  Box,
  Button,
  IconButton,
  Tooltip,
  ToggleButtonGroup,
  ToggleButton,
} from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useNotification } from '../../hooks/useNotification'

interface CdsCardBuilderProps {
  expressions: string[]
  onInsert: (cqlSnippet: string) => void
  onCancel: () => void
}

type FieldMode = 'literal' | 'expression'

interface FieldState {
  value: string
  mode: FieldMode
}

const INDICATORS = ['info', 'warning', 'critical'] as const

function escapeCqlString(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "\\'")
}

function formatFieldValue(field: FieldState): string {
  if (!field.value) return ''
  return field.mode === 'literal'
    ? `'${escapeCqlString(field.value)}'`
    : field.value
}

export default function CdsCardBuilder({ expressions, onInsert, onCancel }: CdsCardBuilderProps) {
  const { showNotification } = useNotification()
  const [name, setName] = useState('Card')
  const [summary, setSummary] = useState<FieldState>({ value: '', mode: 'literal' })
  const [detail, setDetail] = useState<FieldState>({ value: '', mode: 'literal' })
  const [indicator, setIndicator] = useState<string>('info')
  const [sourceLabel, setSourceLabel] = useState<FieldState>({ value: '', mode: 'literal' })

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

    return `define "${name}":\n  Tuple {\n${fields.join(',\n')}\n  }`
  }, [name, summary, detail, indicator, sourceLabel])

  const handleInsert = () => {
    if (!cqlPreview) return
    onInsert(cqlPreview)
  }

  const toggleSx = { textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem', minWidth: 0 } as const

  return (
    <Stack spacing={1}>
      <TextField
        size="small"
        label="Definition Name"
        value={name}
        onChange={(e) => setName(e.target.value)}
        helperText='Names starting with "Card" are detected as CDS Cards'
      />

      {/* Summary */}
      <Stack spacing={0.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
            Summary *
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={summary.mode}
            onChange={(_, v) => { if (v) setSummary((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>Literal</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>Expr</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {summary.mode === 'literal' ? (
          <TextField
            size="small"
            label="Summary"
            value={summary.value}
            onChange={(e) => setSummary((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label="Summary (Expression)"
            value={summary.value}
            onChange={(e) => setSummary((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
          >
            <MenuItem value="" disabled><em>Select a definition...</em></MenuItem>
            {expressions.map((expr) => (
              <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
            ))}
          </TextField>
        )}
      </Stack>

      {/* Detail */}
      <Stack spacing={0.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
            Detail
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={detail.mode}
            onChange={(_, v) => { if (v) setDetail((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>Literal</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>Expr</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {detail.mode === 'literal' ? (
          <TextField
            size="small"
            label="Detail"
            multiline
            rows={2}
            value={detail.value}
            onChange={(e) => setDetail((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label="Detail (Expression)"
            value={detail.value}
            onChange={(e) => setDetail((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
          >
            <MenuItem value="" disabled><em>Select a definition...</em></MenuItem>
            {expressions.map((expr) => (
              <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
            ))}
          </TextField>
        )}
      </Stack>

      {/* Indicator */}
      <TextField
        select
        size="small"
        label="Indicator"
        value={indicator}
        onChange={(e) => setIndicator(e.target.value)}
      >
        {INDICATORS.map((ind) => (
          <MenuItem key={ind} value={ind}>{ind}</MenuItem>
        ))}
      </TextField>

      {/* Source Label */}
      <Stack spacing={0.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
            Source Label
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={sourceLabel.mode}
            onChange={(_, v) => { if (v) setSourceLabel((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>Literal</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>Expr</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {sourceLabel.mode === 'literal' ? (
          <TextField
            size="small"
            label="Source Label"
            value={sourceLabel.value}
            onChange={(e) => setSourceLabel((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label="Source Label (Expression)"
            value={sourceLabel.value}
            onChange={(e) => setSourceLabel((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
          >
            <MenuItem value="" disabled><em>Select a definition...</em></MenuItem>
            {expressions.map((expr) => (
              <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
            ))}
          </TextField>
        )}
      </Stack>

      {/* CQL Preview */}
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

      {/* Action buttons */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton
          onClick={handleInsert}
          disabled={!cqlPreview}
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
