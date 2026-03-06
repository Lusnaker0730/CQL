import { useTranslation } from 'react-i18next'
import {
  Stack, TextField, MenuItem, Typography, IconButton, Tooltip, Chip, Card, CardContent, Box,
} from '@mui/material'
import { Delete as DeleteIcon } from '@mui/icons-material'
import type { BaseElement } from '../../../types/authoring'

const OPERATORS = [
  { value: '+', label: '+', cql: '+' },
  { value: '-', label: '−', cql: '-' },
  { value: '*', label: '×', cql: '*' },
  { value: '/', label: '÷', cql: '/' },
]

interface ArithmeticElementProps {
  element: BaseElement
  /** Other base elements available as operands */
  availableOperands: { uniqueId: string; name: string; returnType: string }[]
  onUpdate: (updates: Partial<BaseElement>) => void
  onDelete: () => void
}

function getFieldValue(element: BaseElement, fieldId: string): string {
  const field = element.fields?.find((f) => f.id === fieldId)
  return (field?.value as string) || ''
}

function updateField(element: BaseElement, fieldId: string, value: string): BaseElement['fields'] {
  const fields = element.fields || []
  const idx = fields.findIndex((f) => f.id === fieldId)
  if (idx >= 0) {
    return fields.map((f, i) => i === idx ? { ...f, value } : f)
  }
  return [...fields, { id: fieldId, type: 'string', name: fieldId, value }]
}

export default function ArithmeticElement({ element, availableOperands, onUpdate, onDelete }: ArithmeticElementProps) {
  const { t } = useTranslation('authoring')

  const leftId = getFieldValue(element, 'left_operand_id')
  const rightId = getFieldValue(element, 'right_operand_id')
  const operator = getFieldValue(element, 'operator') || '+'

  const numericOperands = availableOperands.filter((op) =>
    op.uniqueId !== element.uniqueId &&
    isNumericReturnType(op.returnType)
  )

  const handleNameChange = (name: string) => onUpdate({ name })

  const handleFieldChange = (fieldId: string, value: string) => {
    onUpdate({ fields: updateField(element, fieldId, value) })
  }

  const leftName = availableOperands.find((o) => o.uniqueId === leftId)?.name
  const rightName = availableOperands.find((o) => o.uniqueId === rightId)?.name
  const preview = leftName && rightName
    ? `"${leftName}" ${operator} "${rightName}"`
    : ''

  return (
    <Card variant="outlined" sx={{ borderLeft: 3, borderLeftColor: 'secondary.main' }}>
      <CardContent>
        <Stack direction="row" alignItems="center" spacing={1} mb={2}>
          <TextField
            value={element.name}
            onChange={(e) => handleNameChange(e.target.value)}
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

        <Stack direction="row" spacing={1} alignItems="center">
          <TextField
            select
            size="small"
            label={t('arithmetic.leftOperand')}
            value={leftId}
            onChange={(e) => handleFieldChange('left_operand_id', e.target.value)}
            sx={{ flex: 1 }}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value="" disabled>
              <em>{t('arithmetic.selectElement')}</em>
            </MenuItem>
            {numericOperands.map((op) => (
              <MenuItem key={op.uniqueId} value={op.uniqueId}>
                {op.name} <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>({op.returnType})</Typography>
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size="small"
            label={t('arithmetic.operator')}
            value={operator}
            onChange={(e) => handleFieldChange('operator', e.target.value)}
            sx={{ width: 80 }}
          >
            {OPERATORS.map((op) => (
              <MenuItem key={op.value} value={op.value} sx={{ fontSize: '1.1rem', fontWeight: 600 }}>
                {op.label}
              </MenuItem>
            ))}
          </TextField>

          <TextField
            select
            size="small"
            label={t('arithmetic.rightOperand')}
            value={rightId}
            onChange={(e) => handleFieldChange('right_operand_id', e.target.value)}
            sx={{ flex: 1 }}
            SelectProps={{ displayEmpty: true }}
            InputLabelProps={{ shrink: true }}
          >
            <MenuItem value="" disabled>
              <em>{t('arithmetic.selectElement')}</em>
            </MenuItem>
            {numericOperands.map((op) => (
              <MenuItem key={op.uniqueId} value={op.uniqueId}>
                {op.name} <Typography component="span" variant="caption" color="text.secondary" sx={{ ml: 0.5 }}>({op.returnType})</Typography>
              </MenuItem>
            ))}
          </TextField>
        </Stack>

        {preview && (
          <Box sx={{ mt: 1.5, p: 1, bgcolor: 'rgba(13,115,119,0.04)', borderRadius: 1 }}>
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

function isNumericReturnType(rt: string): boolean {
  const numeric = [
    'system_quantity', 'integer', 'decimal', 'system_integer', 'system_decimal',
    'observation', 'list_of_observations',
  ]
  // Allow any type — the user knows what they're doing; CQL engine will type-check
  // But highlight numeric types as preferred
  return numeric.includes(rt) || rt === 'boolean' || !rt.startsWith('list_of_')
}
