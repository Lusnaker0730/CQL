import { useMemo } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack, TextField, MenuItem, Typography, IconButton, Tooltip, Chip, Card, CardContent, Box, Button,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
// Sub-path icon imports (not barrel): see PR #501 — barrel `from '@mui/icons-material'`
// triggers Vite to enumerate 10k icon files at test-collection time.
import DeleteIcon from '@mui/icons-material/Delete'
import RemoveIcon from '@mui/icons-material/RemoveCircleOutline'
import AddIcon from '@mui/icons-material/AddCircleOutline'
import type { BaseElement, ElementField } from '../../../types/authoring'
import { escapeCqlIdentifier } from '../../../utils/cqlString'
import OperandField from './OperandField'
import type { OperandMode } from './operandValidation'
import {
  ARITHMETIC_OPERATORS,
  NARY_MAX_OPERANDS,
  NARY_MIN_OPERANDS,
  convertLegacy2aryToNary,
  emitNaryArithmeticCql,
  type NaryOperand,
} from './arithmeticEmit'

// PAT-163: operator display labels. CQL keyword operators (mod/div) and the
// power symbol stay as-is; +/-/*/÷ get unicode display chars for clarity.
const OPERATOR_LABELS: Record<string, string> = {
  '+': '+',
  '-': '−',
  '*': '×',
  '/': '÷',
  mod: 'mod',
  div: 'div',
  '^': '^',
}

interface ArithmeticElementProps {
  element: BaseElement
  availableOperands: { uniqueId: string; name: string; returnType: string }[]
  onUpdate: (updates: Partial<BaseElement>) => void
  onDelete: () => void
}

function getFieldRawValue(fields: ElementField[] | undefined, fieldId: string): unknown {
  return fields?.find((f) => f.id === fieldId)?.value
}

/**
 * PAT-163: Read operands[] + operators[] from element.fields, falling back to
 * legacy left_x / right_x / operator scalars if the new shape isn't present.
 * Returns the in-memory N-ary representation regardless of on-disk shape.
 */
function readOperandsAndOperators(element: BaseElement): {
  operands: NaryOperand[]
  operators: string[]
} {
  const fields = element.fields
  const operandsRaw = getFieldRawValue(fields, 'operands')
  const operatorsRaw = getFieldRawValue(fields, 'operators')
  if (Array.isArray(operandsRaw) && Array.isArray(operatorsRaw)) {
    return {
      operands: operandsRaw as NaryOperand[],
      operators: operatorsRaw as string[],
    }
  }
  // Legacy 2-ary shape — convert in-memory.
  const legacyValues: Record<string, string> = {}
  for (const f of fields ?? []) {
    if (typeof f.value === 'string') legacyValues[f.id] = f.value
  }
  return convertLegacy2aryToNary(legacyValues)
}

export default function ArithmeticElement({
  element, availableOperands, onUpdate, onDelete,
}: ArithmeticElementProps) {
  const { t } = useTranslation('authoring')

  const { operands, operators } = useMemo(() => readOperandsAndOperators(element), [element])
  const numericOperands = availableOperands.filter((op) => op.uniqueId !== element.uniqueId)

  // Single write path: replace operands + operators fields atomically. Anything
  // legacy (left_x / right_x / operator scalars) gets stripped so the on-disk
  // shape converges to N-ary on next save.
  const writeNary = (newOperands: NaryOperand[], newOperators: string[]) => {
    const otherFields = (element.fields ?? []).filter(
      (f) => !['operands', 'operators',
        'left_mode', 'left_operand_id', 'left_literal',
        'left_literal_value', 'left_literal_unit',
        'right_mode', 'right_operand_id', 'right_literal',
        'right_literal_value', 'right_literal_unit',
        'operator'].includes(f.id),
    )
    const operandsField: ElementField = {
      id: 'operands', type: 'json', name: 'operands', value: newOperands as unknown as string,
    }
    const operatorsField: ElementField = {
      id: 'operators', type: 'json', name: 'operators', value: newOperators as unknown as string,
    }
    onUpdate({ fields: [...otherFields, operandsField, operatorsField] })
  }

  const updateOperand = (idx: number, patch: Partial<NaryOperand>) => {
    const next = operands.map((op, i) => (i === idx ? { ...op, ...patch } : op))
    writeNary(next, operators)
  }

  const updateOperator = (idx: number, op: string) => {
    const next = operators.map((o, i) => (i === idx ? op : o))
    writeNary(operands, next)
  }

  const addOperand = () => {
    if (operands.length >= NARY_MAX_OPERANDS) return
    const newOperand: NaryOperand = { mode: 'element' }
    writeNary([...operands, newOperand], [...operators, '+'])
  }

  const removeOperand = (idx: number) => {
    if (operands.length <= NARY_MIN_OPERANDS) return
    const newOperands = operands.filter((_, i) => i !== idx)
    // When removing operand at idx, drop the operator AFTER it (or BEFORE if it's the last)
    const opIdxToDrop = idx === operands.length - 1 ? idx - 1 : idx
    const newOperators = operators.filter((_, i) => i !== opIdxToDrop)
    writeNary(newOperands, newOperators)
  }

  // CQL preview — share emission with the backend via arithmeticEmit.
  const preview = emitNaryArithmeticCql(
    operands,
    operators,
    numericOperands,
    escapeCqlIdentifier,
  )
  const safeNameDisplay = element.name ? escapeCqlIdentifier(element.name) : ''

  const canRemove = operands.length > NARY_MIN_OPERANDS
  const canAdd = operands.length < NARY_MAX_OPERANDS

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

        <Stack spacing={0.75}>
          {operands.map((operand, idx) => (
            <Box key={idx}>
              {idx > 0 && (
                <Stack direction="row" alignItems="center" sx={{ pl: 1, my: 0.5 }}>
                  <TextField
                    select
                    size="small"
                    label={t('arithmetic.operator')}
                    value={operators[idx - 1]}
                    onChange={(e) => updateOperator(idx - 1, e.target.value)}
                    sx={{ width: 110 }}
                  >
                    {ARITHMETIC_OPERATORS.map((op) => (
                      <MenuItem key={op} value={op} sx={{ fontSize: '1.05rem', fontWeight: 600 }}>
                        {OPERATOR_LABELS[op]}
                      </MenuItem>
                    ))}
                  </TextField>
                </Stack>
              )}

              <Stack direction="row" alignItems="flex-start" spacing={0.5}>
                <Box sx={{ flex: 1 }}>
                  <OperandField
                    mode={operand.mode ?? 'element'}
                    elementId={operand.operand_id ?? ''}
                    literal={operand.operand_literal ?? ''}
                    quantityValue={operand.operand_literal_value ?? ''}
                    quantityUnit={operand.operand_literal_unit ?? ''}
                    label={t('arithmetic.operandLabel', { number: idx + 1, defaultValue: `Operand ${idx + 1}` })}
                    operands={numericOperands}
                    selectPlaceholder={t('arithmetic.selectElement')}
                    literalLabel={t('arithmetic.literalValue')}
                    modeElementLabel={t('arithmetic.modeElement')}
                    modeLiteralLabel={t('arithmetic.modeLiteral')}
                    modeQuantityLabel={t('arithmetic.modeQuantity')}
                    quantityValueLabel={t('arithmetic.quantityValue')}
                    quantityUnitLabel={t('arithmetic.quantityUnit')}
                    onModeChange={(mode: OperandMode) => updateOperand(idx, { mode })}
                    onElementChange={(id) => updateOperand(idx, { operand_id: id })}
                    onLiteralChange={(val) => updateOperand(idx, { operand_literal: val })}
                    onQuantityValueChange={(val) => updateOperand(idx, { operand_literal_value: val })}
                    onQuantityUnitChange={(val) => updateOperand(idx, { operand_literal_unit: val })}
                  />
                </Box>
                <Tooltip title={t('arithmetic.removeOperand')}>
                  <span>
                    <IconButton
                      size="small"
                      onClick={() => removeOperand(idx)}
                      disabled={!canRemove}
                      aria-label={t('arithmetic.removeOperand')}
                      sx={{ mt: 0.5 }}
                    >
                      <RemoveIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
              </Stack>
            </Box>
          ))}

          <Box>
            <Button
              size="small"
              startIcon={<AddIcon />}
              onClick={addOperand}
              disabled={!canAdd}
              sx={{ textTransform: 'none' }}
            >
              {t('arithmetic.addOperand')}
            </Button>
          </Box>
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
