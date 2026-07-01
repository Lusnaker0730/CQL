import {
  Stack, TextField, MenuItem, Typography,
  ToggleButtonGroup, ToggleButton,
} from '@mui/material'
import UcumUnitField from '../fields/UcumUnitField'
import type { OperandMode } from './operandValidation'

interface OperandFieldProps {
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
}

/**
 * Shared operand input used by ArithmeticElement (PAT-161) and
 * ArithmeticUnaryElement (PAT-162). 3 modes:
 *   - element: dropdown of other base elements
 *   - literal: numeric input
 *   - quantity: numeric value + UCUM unit picker (with injection-safe regex)
 */
export default function OperandField({
  mode, elementId, literal, quantityValue, quantityUnit, label, operands, selectPlaceholder,
  literalLabel, modeElementLabel, modeLiteralLabel, modeQuantityLabel,
  quantityValueLabel, quantityUnitLabel,
  onModeChange, onElementChange, onLiteralChange, onQuantityValueChange, onQuantityUnitChange,
}: OperandFieldProps) {
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
          slotProps={{
            select: { displayEmpty: true },
            inputLabel: { shrink: true }
          }}>
          <MenuItem value="" disabled>
            <em>{selectPlaceholder}</em>
          </MenuItem>
          {operands.map((op) => (
            <MenuItem key={op.uniqueId} value={op.uniqueId}>
              {op.name}
              <Typography
                component="span"
                variant="caption"
                sx={{
                  color: "text.secondary",
                  ml: 0.5
                }}>
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
  );
}
