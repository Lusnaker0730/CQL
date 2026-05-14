import { useTranslation } from 'react-i18next'
import {
  Stack, TextField, MenuItem, Typography, IconButton, Tooltip, Chip, Card, CardContent, Box,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
// Sub-path import (not barrel): keeps vitest from pre-bundling all 10k icons.
// `import { Delete } from '@mui/icons-material'` forces Vite to scan the
// entire barrel at collection time, which hangs on Windows (EMFILE) and
// is very slow in CI. Per-icon imports resolve to one file only.
import DeleteIcon from '@mui/icons-material/Delete'
import type { BaseElement } from '../../../types/authoring'
import { escapeCqlIdentifier } from '../../../utils/cqlString'
import UcumUnitField from '../fields/UcumUnitField'

const NUMERIC_LITERAL_RE = /^-?\d+(\.\d+)?$/
// PAT-161: UCUM unit allow-list — letters/digits + structural chars only;
// single quote MUST be excluded (closes the CQL Quantity literal). Mirrors the
// backend ARITHMETIC_UCUM_UNIT_PATTERN.
const UCUM_UNIT_RE = /^[A-Za-z0-9./*+\-()[\]{}%_]{1,32}$/

const OPERATORS = [
  { value: '+', label: '+' },
  { value: '-', label: '−' },
  { value: '*', label: '×' },
  { value: '/', label: '÷' },
  { value: 'mod', label: 'mod' },
  { value: 'div', label: 'div' },
  { value: '^', label: '^' },
]

type OperandMode = 'element' | 'literal' | 'quantity'

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

  const leftMode = (getFieldValue(element, 'left_mode') || 'element') as OperandMode
  const rightMode = (getFieldValue(element, 'right_mode') || 'element') as OperandMode
  const leftId = getFieldValue(element, 'left_operand_id')
  const rightId = getFieldValue(element, 'right_operand_id')
  const leftLiteral = getFieldValue(element, 'left_literal')
  const rightLiteral = getFieldValue(element, 'right_literal')
  const leftQuantityValue = getFieldValue(element, 'left_literal_value')
  const rightQuantityValue = getFieldValue(element, 'right_literal_value')
  const leftQuantityUnit = getFieldValue(element, 'left_literal_unit')
  const rightQuantityUnit = getFieldValue(element, 'right_literal_unit')
  const operator = getFieldValue(element, 'operator') || '+'

  const numericOperands = availableOperands.filter((op) =>
    op.uniqueId !== element.uniqueId
  )

  const handleFieldChange = (updates: Record<string, string>) => {
    onUpdate({ fields: updateFields(element, updates) })
  }

  // Build preview. Identifier names get escaped (`"` / `\` safe). Literals
  // are emitted only if they parse as a CQL numeric literal — otherwise the
  // preview shows nothing rather than producing un-translatable CQL.
  // Quantity operands require both a numeric value AND a UCUM-safe unit.
  const leftElementName = availableOperands.find((o) => o.uniqueId === leftId)?.name
  const rightElementName = availableOperands.find((o) => o.uniqueId === rightId)?.name
  const literalToCql = (raw: string): string => NUMERIC_LITERAL_RE.test(raw.trim()) ? raw.trim() : ''
  const quantityToCql = (value: string, unit: string): string => {
    const v = value.trim()
    const u = unit.trim()
    if (!NUMERIC_LITERAL_RE.test(v) || !UCUM_UNIT_RE.test(u)) return ''
    return `${v} '${u}'`
  }
  const operandToCql = (
    mode: OperandMode,
    elementName: string | undefined,
    literal: string,
    quantityValue: string,
    quantityUnit: string,
  ): string => {
    if (mode === 'literal') return literalToCql(literal)
    if (mode === 'quantity') return quantityToCql(quantityValue, quantityUnit)
    return elementName ? `"${escapeCqlIdentifier(elementName)}"` : ''
  }
  const leftCql = operandToCql(leftMode, leftElementName, leftLiteral, leftQuantityValue, leftQuantityUnit)
  const rightCql = operandToCql(rightMode, rightElementName, rightLiteral, rightQuantityValue, rightQuantityUnit)
  const preview = leftCql && rightCql ? `${leftCql} ${operator} ${rightCql}` : ''
  const safeNameDisplay = element.name ? escapeCqlIdentifier(element.name) : ''

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
            mode={leftMode}
            elementId={leftId}
            literal={leftLiteral}
            quantityValue={leftQuantityValue}
            quantityUnit={leftQuantityUnit}
            label={t('arithmetic.leftOperand')}
            operands={numericOperands}
            selectPlaceholder={t('arithmetic.selectElement')}
            literalLabel={t('arithmetic.literalValue')}
            modeElementLabel={t('arithmetic.modeElement')}
            modeLiteralLabel={t('arithmetic.modeLiteral')}
            modeQuantityLabel={t('arithmetic.modeQuantity')}
            quantityValueLabel={t('arithmetic.quantityValue')}
            quantityUnitLabel={t('arithmetic.quantityUnit')}
            onModeChange={(mode) => handleFieldChange({ left_mode: mode })}
            onElementChange={(id) => handleFieldChange({ left_operand_id: id })}
            onLiteralChange={(val) => handleFieldChange({ left_literal: val })}
            onQuantityValueChange={(val) => handleFieldChange({ left_literal_value: val })}
            onQuantityUnitChange={(val) => handleFieldChange({ left_literal_unit: val })}
          />

          {/* Operator */}
          <TextField
            select
            size="small"
            label={t('arithmetic.operator')}
            value={operator}
            onChange={(e) => handleFieldChange({ operator: e.target.value })}
            sx={{ width: 96, mt: '28px !important' }}
          >
            {OPERATORS.map((op) => (
              <MenuItem key={op.value} value={op.value} sx={{ fontSize: '1.05rem', fontWeight: 600 }}>
                {op.label}
              </MenuItem>
            ))}
          </TextField>

          {/* Right operand */}
          <OperandField
            mode={rightMode}
            elementId={rightId}
            literal={rightLiteral}
            quantityValue={rightQuantityValue}
            quantityUnit={rightQuantityUnit}
            label={t('arithmetic.rightOperand')}
            operands={numericOperands}
            selectPlaceholder={t('arithmetic.selectElement')}
            literalLabel={t('arithmetic.literalValue')}
            modeElementLabel={t('arithmetic.modeElement')}
            modeLiteralLabel={t('arithmetic.modeLiteral')}
            modeQuantityLabel={t('arithmetic.modeQuantity')}
            quantityValueLabel={t('arithmetic.quantityValue')}
            quantityUnitLabel={t('arithmetic.quantityUnit')}
            onModeChange={(mode) => handleFieldChange({ right_mode: mode })}
            onElementChange={(id) => handleFieldChange({ right_operand_id: id })}
            onLiteralChange={(val) => handleFieldChange({ right_literal: val })}
            onQuantityValueChange={(val) => handleFieldChange({ right_literal_value: val })}
            onQuantityUnitChange={(val) => handleFieldChange({ right_literal_unit: val })}
          />
        </Stack>

        {preview && (
          <Box sx={(theme) => ({ mt: 1.5, p: 1, bgcolor: alpha(theme.palette.primary.main, 0.04), borderRadius: 1 })}>
            <Typography variant="caption" color="text.secondary">{t('arithmetic.cqlPreview')}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
              define &quot;{safeNameDisplay}&quot;: {preview}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  )
}

function OperandField({
  mode, elementId, literal, quantityValue, quantityUnit, label, operands, selectPlaceholder,
  literalLabel, modeElementLabel, modeLiteralLabel, modeQuantityLabel,
  quantityValueLabel, quantityUnitLabel,
  onModeChange, onElementChange, onLiteralChange, onQuantityValueChange, onQuantityUnitChange,
}: {
  mode: OperandMode
  elementId: string
  literal: string
  quantityValue: string
  quantityUnit: string
  label: string
  operands: { uniqueId: string; name: string; returnType: string }[]
  selectPlaceholder: string
  literalLabel: string
  modeElementLabel: string
  modeLiteralLabel: string
  modeQuantityLabel: string
  quantityValueLabel: string
  quantityUnitLabel: string
  onModeChange: (mode: OperandMode) => void
  onElementChange: (id: string) => void
  onLiteralChange: (val: string) => void
  onQuantityValueChange: (val: string) => void
  onQuantityUnitChange: (val: string) => void
}) {
  return (
    <Stack spacing={0.5} sx={{ flex: 1 }}>
      <ToggleButtonGroup
        size="small"
        exclusive
        value={mode}
        onChange={(_, v: OperandMode | null) => { if (v) onModeChange(v) }}
      >
        <ToggleButton value="element" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
          {modeElementLabel}
        </ToggleButton>
        <ToggleButton value="literal" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
          {modeLiteralLabel}
        </ToggleButton>
        <ToggleButton value="quantity" sx={{ textTransform: 'none', px: 1, py: 0, fontSize: '0.7rem' }}>
          {modeQuantityLabel}
        </ToggleButton>
      </ToggleButtonGroup>

      {mode === 'element' && (
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
      )}

      {mode === 'literal' && (
        <TextField
          size="small"
          label={literalLabel}
          value={literal}
          onChange={(e) => onLiteralChange(e.target.value)}
          placeholder="100"
          sx={{ '& input': { fontFamily: 'monospace', fontSize: '0.85rem' } }}
        />
      )}

      {mode === 'quantity' && (
        <Stack direction="row" spacing={0.5}>
          <TextField
            size="small"
            label={quantityValueLabel}
            value={quantityValue}
            onChange={(e) => onQuantityValueChange(e.target.value)}
            placeholder="5"
            sx={{ width: 80, '& input': { fontFamily: 'monospace', fontSize: '0.85rem' } }}
          />
          <UcumUnitField
            value={quantityUnit}
            onChange={onQuantityUnitChange}
            label={quantityUnitLabel}
            size="small"
            fullWidth
          />
        </Stack>
      )}
    </Stack>
  )
}
