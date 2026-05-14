import { useTranslation } from 'react-i18next'
import {
  Stack, TextField, MenuItem, Typography, IconButton, Tooltip, Chip, Card, CardContent, Box,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import DeleteIcon from '@mui/icons-material/Delete'
import type { BaseElement } from '../../../types/authoring'
import { escapeCqlIdentifier } from '../../../utils/cqlString'
import OperandField from './OperandField'
import { NUMERIC_LITERAL_RE, quantityToCql, type OperandMode } from './operandValidation'

// PAT-162: unary CQL functions taking one Decimal/Integer/Quantity argument.
// Kept alphabetical for predictable dropdown order; mirrors the backend
// UNARY_FUNCTIONS set in ExpressionCqlEngine.
const UNARY_FUNCTIONS = ['Abs', 'Ceiling', 'Floor', 'Negate', 'Round', 'Truncate'] as const

interface ArithmeticUnaryElementProps {
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

export default function ArithmeticUnaryElement({
  element, availableOperands, onUpdate, onDelete,
}: ArithmeticUnaryElementProps) {
  const { t } = useTranslation('authoring')

  const fn = (getFieldValue(element, 'function') as typeof UNARY_FUNCTIONS[number]) || 'Abs'
  const operandMode = (getFieldValue(element, 'operand_mode') || 'element') as OperandMode
  const operandId = getFieldValue(element, 'operand_id')
  const operandLiteral = getFieldValue(element, 'operand_literal')
  const operandQuantityValue = getFieldValue(element, 'operand_literal_value')
  const operandQuantityUnit = getFieldValue(element, 'operand_literal_unit')

  const numericOperands = availableOperands.filter((op) => op.uniqueId !== element.uniqueId)

  const handleFieldChange = (updates: Record<string, string>) => {
    onUpdate({ fields: updateFields(element, updates) })
  }

  // Build preview. Same defense pattern as ArithmeticElement: identifier names
  // get escaped; literals must parse as numeric; quantities must pass UCUM regex.
  // If the operand can't be resolved, the preview box is hidden entirely rather
  // than emitting un-translatable CQL.
  const operandElementName = availableOperands.find((o) => o.uniqueId === operandId)?.name
  const literalToCql = (raw: string): string => NUMERIC_LITERAL_RE.test(raw.trim()) ? raw.trim() : ''
  const operandCql = operandMode === 'literal'
    ? literalToCql(operandLiteral)
    : operandMode === 'quantity'
      ? quantityToCql(operandQuantityValue, operandQuantityUnit)
      : operandElementName ? `"${escapeCqlIdentifier(operandElementName)}"` : ''
  const safeFn = (UNARY_FUNCTIONS as readonly string[]).includes(fn) ? fn : 'Abs'
  const preview = operandCql ? `${safeFn}(${operandCql})` : ''
  const safeNameDisplay = element.name ? escapeCqlIdentifier(element.name) : ''

  return (
    <Card variant="outlined" sx={{ borderLeft: 3, borderLeftColor: 'info.main' }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} mb={2}>
          <TextField
            value={element.name}
            onChange={(e) => onUpdate({ name: e.target.value })}
            size="small"
            variant="standard"
            sx={{ '& .MuiInput-input': { fontWeight: 600 } }}
            placeholder={t('arithmeticUnary.namePlaceholder')}
          />
          <Chip label={element.returnType} size="small" variant="outlined" color="info" />
          <Box sx={{ flex: 1 }} />
          <Tooltip title={t('baseElements.removeTooltip')}>
            <IconButton size="small" color="error" onClick={onDelete} aria-label={t('baseElements.removeTooltip')}>
              <DeleteIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>

        <Stack direction="row" spacing={1} alignItems="flex-start">
          {/* Function dropdown */}
          <TextField
            select
            size="small"
            label={t('arithmeticUnary.function')}
            value={safeFn}
            onChange={(e) => handleFieldChange({ function: e.target.value })}
            sx={{ width: 140, mt: '28px !important' }}
          >
            {UNARY_FUNCTIONS.map((f) => (
              <MenuItem key={f} value={f} sx={{ fontSize: '0.95rem', fontWeight: 500 }}>
                {f}
              </MenuItem>
            ))}
          </TextField>

          {/* Single operand */}
          <OperandField
            mode={operandMode}
            elementId={operandId}
            literal={operandLiteral}
            quantityValue={operandQuantityValue}
            quantityUnit={operandQuantityUnit}
            label={t('arithmeticUnary.operand')}
            operands={numericOperands}
            selectPlaceholder={t('arithmetic.selectElement')}
            literalLabel={t('arithmetic.literalValue')}
            modeElementLabel={t('arithmetic.modeElement')}
            modeLiteralLabel={t('arithmetic.modeLiteral')}
            modeQuantityLabel={t('arithmetic.modeQuantity')}
            quantityValueLabel={t('arithmetic.quantityValue')}
            quantityUnitLabel={t('arithmetic.quantityUnit')}
            onModeChange={(mode) => handleFieldChange({ operand_mode: mode })}
            onElementChange={(id) => handleFieldChange({ operand_id: id })}
            onLiteralChange={(val) => handleFieldChange({ operand_literal: val })}
            onQuantityValueChange={(val) => handleFieldChange({ operand_literal_value: val })}
            onQuantityUnitChange={(val) => handleFieldChange({ operand_literal_unit: val })}
          />
        </Stack>

        {preview && (
          <Box sx={(theme) => ({ mt: 1.5, p: 1, bgcolor: alpha(theme.palette.info.main, 0.04), borderRadius: 1 })}>
            <Typography variant="caption" color="text.secondary">{t('arithmeticUnary.cqlPreview')}</Typography>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
              define &quot;{safeNameDisplay}&quot;: {preview}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  )
}
