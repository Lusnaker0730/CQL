import { useTranslation } from 'react-i18next'
import {
  Stack, TextField, MenuItem, Typography, IconButton, Tooltip, Chip, Card, CardContent, Box,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import { Delete as DeleteIcon } from '@mui/icons-material'
import type { BaseElement } from '../../../types/authoring'

const OPERATORS = [
  { value: '+', label: '+' },
  { value: '-', label: '−' },
  { value: '*', label: '×' },
  { value: '/', label: '÷' },
]

interface ArithmeticElementProps {
  element: BaseElement
  availableOperands: { uniqueId: string; name: string; returnType: string }[]
  onUpdate: (updates: Partial<BaseElement>) => void
  onDelete: () => void
}

function getFieldValue(element: BaseElement, fieldId: string): string {
  const field = element.fields?.find((f) => f.id === fieldId)
  return (field?.value as string) || ''
}

function updateFields(element: BaseElement, updates: Record<string, string>): BaseElement['fields'] {
  const fields = [...(element.fields || [])]
  for (const [fieldId, value] of Object.entries(updates)) {
    const idx = fields.findIndex((f) => f.id === fieldId)
    if (idx >= 0) {
      fields[idx] = { ...fields[idx], value }
    } else {
      fields.push({ id: fieldId, type: 'string', name: fieldId, value })
    }
  }
  return fields
}

export default function ArithmeticElement({ element, availableOperands, onUpdate, onDelete }: ArithmeticElementProps) {
  const { t } = useTranslation('authoring')

  const leftMode = getFieldValue(element, 'left_mode') || 'element'
  const rightMode = getFieldValue(element, 'right_mode') || 'element'
  const leftId = getFieldValue(element, 'left_operand_id')
  const rightId = getFieldValue(element, 'right_operand_id')
  const leftLiteral = getFieldValue(element, 'left_literal')
  const rightLiteral = getFieldValue(element, 'right_literal')
  const operator = getFieldValue(element, 'operator') || '+'

  const numericOperands = availableOperands.filter((op) =>
    op.uniqueId !== element.uniqueId
  )

  const handleFieldChange = (updates: Record<string, string>) => {
    onUpdate({ fields: updateFields(element, updates) })
  }

  // Build preview
  const leftName = leftMode === 'literal' ? leftLiteral : availableOperands.find((o) => o.uniqueId === leftId)?.name
  const rightName = rightMode === 'literal' ? rightLiteral : availableOperands.find((o) => o.uniqueId === rightId)?.name
  const leftCql = leftMode === 'literal' && leftLiteral ? leftLiteral : leftName ? `"${leftName}"` : ''
  const rightCql = rightMode === 'literal' && rightLiteral ? rightLiteral : rightName ? `"${rightName}"` : ''
  const preview = leftCql && rightCql ? `${leftCql} ${operator} ${rightCql}` : ''

  return (
    <Card variant="outlined" sx={{ borderLeft: 3, borderLeftColor: 'secondary.main' }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} mb={2}>
          <TextField
            value={element.name}
            onChange={(e) => onUpdate({ name: e.target.value })}
            size="small"
            variant="standard"
            sx={{ '& .MuiInput-input': { fontWeight: 600 } }}
            placeholder={t('arithmetic.namePlaceholder')}
          />
          <Chip label={element.returnType} size="small" variant="outlined" color="secondary" />
          <Box sx={{ flex: 1 }} />
          <Tooltip title={t('baseElements.removeTooltip')}>
            <IconButton size="small" color="error" onClick={onDelete} aria-label={t('baseElements.removeTooltip')}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>

        <Stack direction="row" spacing={1} alignItems="flex-start">
          {/* Left operand */}
          <OperandField
            mode={leftMode as 'element' | 'literal'}
            elementId={leftId}
            literal={leftLiteral}
            label={t('arithmetic.leftOperand')}
            operands={numericOperands}
            selectPlaceholder={t('arithmetic.selectElement')}
            literalLabel={t('arithmetic.literalValue')}
            modeElementLabel={t('arithmetic.modeElement')}
            modeLiteralLabel={t('arithmetic.modeLiteral')}
            onModeChange={(mode) => handleFieldChange({ left_mode: mode })}
            onElementChange={(id) => handleFieldChange({ left_operand_id: id })}
            onLiteralChange={(val) => handleFieldChange({ left_literal: val })}
          />

          {/* Operator */}
          <TextField
            select
            size="small"
            label={t('arithmetic.operator')}
            value={operator}
            onChange={(e) => handleFieldChange({ operator: e.target.value })}
            sx={{ width: 80, mt: '28px !important' }}
          >
            {OPERATORS.map((op) => (
              <MenuItem key={op.value} value={op.value} sx={{ fontSize: '1.1rem', fontWeight: 600 }}>
                {op.label}
              </MenuItem>
            ))}
          </TextField>

          {/* Right operand */}
          <OperandField
            mode={rightMode as 'element' | 'literal'}
            elementId={rightId}
            literal={rightLiteral}
            label={t('arithmetic.rightOperand')}
            operands={numericOperands}
            selectPlaceholder={t('arithmetic.selectElement')}
            literalLabel={t('arithmetic.literalValue')}
            modeElementLabel={t('arithmetic.modeElement')}
            modeLiteralLabel={t('arithmetic.modeLiteral')}
            onModeChange={(mode) => handleFieldChange({ right_mode: mode })}
            onElementChange={(id) => handleFieldChange({ right_operand_id: id })}
            onLiteralChange={(val) => handleFieldChange({ right_literal: val })}
          />
        </Stack>

        {preview && (
          <Box sx={(theme) => ({ mt: 1.5, p: 1, bgcolor: alpha(theme.palette.primary.main, 0.04), borderRadius: 1 })}>
            <Typography variant="caption" color="text.secondary">{t('arithmetic.cqlPreview')}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
              define &quot;{element.name}&quot;: {preview}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  )
}

function OperandField({
  mode, elementId, literal, label, operands, selectPlaceholder,
  literalLabel, modeElementLabel, modeLiteralLabel,
  onModeChange, onElementChange, onLiteralChange,
}: {
  mode: 'element' | 'literal'
  elementId: string
  literal: string
  label: string
  operands: { uniqueId: string; name: string; returnType: string }[]
  selectPlaceholder: string
  literalLabel: string
  modeElementLabel: string
  modeLiteralLabel: string
  onModeChange: (mode: 'element' | 'literal') => void
  onElementChange: (id: string) => void
  onLiteralChange: (val: string) => void
}) {
  return (
    <Stack spacing={0.5} sx={{ flex: 1 }}>
      <ToggleButtonGroup
        size="small"
        exclusive
        value={mode}
        onChange={(_, v) => { if (v) onModeChange(v) }}
      >
        <ToggleButton value="element" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
          {modeElementLabel}
        </ToggleButton>
        <ToggleButton value="literal" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
          {modeLiteralLabel}
        </ToggleButton>
      </ToggleButtonGroup>

      {mode === 'element' ? (
        <TextField
          select
          size="small"
          label={label}
          value={elementId}
          onChange={(e) => onElementChange(e.target.value)}
          SelectProps={{ displayEmpty: true }}
          InputLabelProps={{ shrink: true }}
        >
          <MenuItem value="" disabled>
            <em>{selectPlaceholder}</em>
          </MenuItem>
          {operands.map((op) => (
            <MenuItem key={op.uniqueId} value={op.uniqueId}>
              {op.name}
              <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>
                ({op.returnType})
              </Typography>
            </MenuItem>
          ))}
        </TextField>
      ) : (
        <TextField
          size="small"
          label={literalLabel}
          value={literal}
          onChange={(e) => onLiteralChange(e.target.value)}
          placeholder="100"
          sx={{ '& input': { fontFamily: 'monospace', fontSize: '0.85rem' } }}
        />
      )}
    </Stack>
  )
}
