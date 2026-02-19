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
} from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
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
  const { t } = useTranslation('builder')
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
        label={t('definitions.name')}
        value={name}
        onChange={(e) => setName(e.target.value)}
        helperText={t('cdsCard.nameHint')}
      />

      {/* Summary */}
      <Stack spacing={0.5}>
        <Stack direction="row" alignItems="center" spacing={1}>
          <Typography variant="caption" fontWeight={600} color="text.secondary" sx={{ flex: 1 }}>
            {t('cdsCard.summary')}
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={summary.mode}
            onChange={(_, v) => { if (v) setSummary((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>{t('cdsCard.literal')}</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>{t('cdsCard.expr')}</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {summary.mode === 'literal' ? (
          <TextField
            size="small"
            label={t('cdsCard.summaryLabel')}
            value={summary.value}
            onChange={(e) => setSummary((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label={t('cdsCard.summaryExpr')}
            value={summary.value}
            onChange={(e) => setSummary((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value="" disabled><em>{t('cdsCard.selectDefinition')}</em></MenuItem>
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
            {t('cdsCard.detail')}
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={detail.mode}
            onChange={(_, v) => { if (v) setDetail((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>{t('cdsCard.literal')}</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>{t('cdsCard.expr')}</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {detail.mode === 'literal' ? (
          <TextField
            size="small"
            label={t('cdsCard.detailLabel')}
            multiline
            rows={2}
            value={detail.value}
            onChange={(e) => setDetail((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label={t('cdsCard.detailExpr')}
            value={detail.value}
            onChange={(e) => setDetail((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value="" disabled><em>{t('cdsCard.selectDefinition')}</em></MenuItem>
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
        label={t('cdsCard.indicator')}
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
            {t('cdsCard.sourceLabel')}
          </Typography>
          <ToggleButtonGroup
            size="small"
            exclusive
            value={sourceLabel.mode}
            onChange={(_, v) => { if (v) setSourceLabel((s) => ({ ...s, mode: v })) }}
          >
            <ToggleButton value="literal" sx={toggleSx}>{t('cdsCard.literal')}</ToggleButton>
            <ToggleButton value="expression" sx={toggleSx}>{t('cdsCard.expr')}</ToggleButton>
          </ToggleButtonGroup>
        </Stack>
        {sourceLabel.mode === 'literal' ? (
          <TextField
            size="small"
            label={t('cdsCard.sourceLabelLabel')}
            value={sourceLabel.value}
            onChange={(e) => setSourceLabel((s) => ({ ...s, value: e.target.value }))}
          />
        ) : (
          <TextField
            select
            size="small"
            label={t('cdsCard.sourceLabelExpr')}
            value={sourceLabel.value}
            onChange={(e) => setSourceLabel((s) => ({ ...s, value: e.target.value }))}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value="" disabled><em>{t('cdsCard.selectDefinition')}</em></MenuItem>
            {expressions.map((expr) => (
              <MenuItem key={expr} value={`"${expr}"`}>{expr}</MenuItem>
            ))}
          </TextField>
        )}
      </Stack>

      {/* CQL Preview */}
      <CqlPreviewBox code={cqlPreview} />

      {/* Action buttons */}
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton
          onClick={handleInsert}
          disabled={!cqlPreview}
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
