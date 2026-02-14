import { useState } from 'react'
import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel,
  Dialog, DialogTitle, DialogContent, DialogContentText, DialogActions, Button,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import UcumUnitField from '../fields/UcumUnitField'
import type { Parameter } from '../../../types/authoring'

function generateId(): string {
  return crypto.randomUUID()
}

const PARAMETER_TYPES = [
  { value: 'boolean', label: 'Boolean', hint: 'True/false flag (e.g., enable screening)' },
  { value: 'integer', label: 'Integer', hint: 'Whole number (e.g., age threshold)' },
  { value: 'decimal', label: 'Decimal', hint: 'Decimal number (e.g., BMI cutoff)' },
  { value: 'string', label: 'String', hint: 'Free text value' },
  { value: 'datetime', label: 'DateTime', hint: 'Date and time value' },
  { value: 'time', label: 'Time', hint: 'Time of day value' },
  { value: 'code', label: 'Code', hint: 'Single coded value (system + code)' },
  { value: 'concept', label: 'Concept', hint: 'Coded concept with display text' },
  { value: 'quantity', label: 'Quantity', hint: 'Numeric value with unit (e.g., 10 mg)' },
  { value: 'interval<integer>', label: 'Interval<Integer>', hint: 'Range of integers (e.g., 18-65)' },
  { value: 'interval<datetime>', label: 'Interval<DateTime>', hint: 'Date range (e.g., reporting period)' },
]

interface ParametersProps {
  parameters: Parameter[]
  onChange: (parameters: Parameter[]) => void
}

export default function Parameters({ parameters, onChange }: ParametersProps) {
  const [pendingDeleteId, setPendingDeleteId] = useState<string | null>(null)
  const pendingDeleteName = pendingDeleteId
    ? parameters.find((p) => p.uniqueId === pendingDeleteId)?.name || 'this parameter'
    : ''

  const handleAdd = () => {
    onChange([
      ...parameters,
      {
        uniqueId: generateId(),
        name: 'Parameter ' + (parameters.length + 1),
        type: 'boolean',
        value: undefined,
        comment: '',
      },
    ])
  }

  const handleRemove = (uniqueId: string) => {
    onChange(parameters.filter((p) => p.uniqueId !== uniqueId))
  }

  const handleUpdate = (uniqueId: string, updates: Partial<Parameter>) => {
    onChange(parameters.map((p) => (p.uniqueId === uniqueId ? { ...p, ...updates } : p)))
  }

  const getNameError = (param: Parameter): string | undefined => {
    if (!param.name.trim()) return 'Name is required'
    if (parameters.some((p) => p.uniqueId !== param.uniqueId && p.name.trim() === param.name.trim()))
      return 'Duplicate parameter name'
    return undefined
  }

  const renderValueField = (param: Parameter) => {
    switch (param.type) {
      case 'boolean':
        return (
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Default</InputLabel>
            <Select
              value={param.value === true ? 'true' : param.value === false ? 'false' : ''}
              label="Default"
              onChange={(e) => {
                const v = e.target.value
                handleUpdate(param.uniqueId, { value: v === 'true' ? true : v === 'false' ? false : undefined })
              }}
            >
              <MenuItem value="">None</MenuItem>
              <MenuItem value="true">True</MenuItem>
              <MenuItem value="false">False</MenuItem>
            </Select>
          </FormControl>
        )
      case 'integer':
      case 'decimal':
        return (
          <TextField
            size="small"
            label="Default Value"
            type="number"
            value={param.value ?? ''}
            onChange={(e) => {
              const v = e.target.value
              handleUpdate(param.uniqueId, {
                value: v === '' ? undefined : param.type === 'integer' ? parseInt(v) : parseFloat(v),
              })
            }}
            sx={{ minWidth: 140 }}
          />
        )
      case 'string':
        return (
          <TextField
            size="small"
            label="Default Value"
            value={(param.value as string) || ''}
            onChange={(e) => handleUpdate(param.uniqueId, { value: e.target.value || undefined })}
            sx={{ minWidth: 200 }}
          />
        )
      case 'code': {
        const codeVal = (param.value as { system?: string; code?: string }) || {}
        return (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label="Code System URI"
              value={codeVal.system || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...codeVal, system: e.target.value || undefined },
              })}
              sx={{ minWidth: 200 }}
              placeholder="http://loinc.org"
            />
            <TextField
              size="small"
              label="Code"
              value={codeVal.code || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...codeVal, code: e.target.value || undefined },
              })}
              sx={{ minWidth: 120 }}
              placeholder="12345-6"
            />
          </Stack>
        )
      }
      case 'concept': {
        const conceptVal = (param.value as { system?: string; code?: string; display?: string }) || {}
        return (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label="Display"
              value={conceptVal.display || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, display: e.target.value || undefined },
              })}
              sx={{ minWidth: 160 }}
              placeholder="Display name"
            />
            <TextField
              size="small"
              label="Code System URI"
              value={conceptVal.system || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, system: e.target.value || undefined },
              })}
              sx={{ minWidth: 180 }}
              placeholder="http://loinc.org"
            />
            <TextField
              size="small"
              label="Code"
              value={conceptVal.code || ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...conceptVal, code: e.target.value || undefined },
              })}
              sx={{ minWidth: 100 }}
              placeholder="12345-6"
            />
          </Stack>
        )
      }
      case 'quantity': {
        const qtyVal = (param.value as { value?: number; unit?: string }) || {}
        return (
          <Stack direction="row" spacing={1}>
            <TextField
              size="small"
              label="Value"
              type="number"
              value={qtyVal.value ?? ''}
              onChange={(e) => handleUpdate(param.uniqueId, {
                value: { ...qtyVal, value: e.target.value === '' ? undefined : parseFloat(e.target.value) },
              })}
              sx={{ width: 120 }}
            />
            <UcumUnitField
              label="Unit"
              value={qtyVal.unit || ''}
              onChange={(unit) => handleUpdate(param.uniqueId, {
                value: { ...qtyVal, unit: unit || undefined },
              })}
              sx={{ width: 200 }}
            />
          </Stack>
        )
      }
      default:
        return (
          <TextField
            size="small"
            label="Default Value"
            value={(param.value as string) || ''}
            onChange={(e) => handleUpdate(param.uniqueId, { value: e.target.value || undefined })}
            sx={{ minWidth: 200 }}
            placeholder={`Enter ${param.type} value...`}
          />
        )
    }
  }

  return (
    <Box>
      <Stack direction="row" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Parameters</Typography>
        <GradientButton startIcon={<AddIcon />} onClick={handleAdd}>
          Add Parameter
        </GradientButton>
      </Stack>

      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Define configurable parameters for your CDS artifact. These become CQL parameters that can be set at runtime.
      </Typography>

      {parameters.length === 0 ? (
        <Box sx={{ textAlign: 'center', py: 4, color: 'text.secondary' }}>
          <Typography variant="body2">
            No parameters defined. Add parameters to make your artifact configurable.
          </Typography>
        </Box>
      ) : (
        <Stack spacing={2}>
          {parameters.map((param) => (
            <Card key={param.uniqueId} variant="outlined">
              <CardContent>
                <Stack direction="row" alignItems="center" spacing={2}>
                  <TextField
                    value={param.name}
                    onChange={(e) => handleUpdate(param.uniqueId, { name: e.target.value })}
                    size="small"
                    label="Name"
                    sx={{ flex: 1 }}
                    error={!!getNameError(param)}
                    helperText={getNameError(param)}
                  />
                  <Tooltip title={PARAMETER_TYPES.find((t) => t.value === param.type)?.hint || ''} placement="top">
                    <FormControl size="small" sx={{ minWidth: 160 }}>
                      <InputLabel>Type</InputLabel>
                      <Select
                        value={param.type}
                        label="Type"
                        onChange={(e) => handleUpdate(param.uniqueId, { type: e.target.value, value: undefined })}
                      >
                        {PARAMETER_TYPES.map((t) => (
                          <MenuItem key={t.value} value={t.value}>
                            <Box>
                              <Typography variant="body2">{t.label}</Typography>
                              <Typography variant="caption" color="text.secondary">{t.hint}</Typography>
                            </Box>
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Tooltip>
                  {renderValueField(param)}
                  <Tooltip title="Remove parameter">
                    <IconButton size="small" color="error" onClick={() => setPendingDeleteId(param.uniqueId)}>
                      <DeleteIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </Stack>
                <TextField
                  value={param.comment || ''}
                  onChange={(e) => handleUpdate(param.uniqueId, { comment: e.target.value })}
                  size="small"
                  label="Comment"
                  fullWidth
                  sx={{ mt: 1.5 }}
                  placeholder="Optional description of this parameter..."
                />
              </CardContent>
            </Card>
          ))}
        </Stack>
      )}

      <Dialog open={!!pendingDeleteId} onClose={() => setPendingDeleteId(null)}>
        <DialogTitle>Delete Parameter</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Are you sure you want to delete <strong>{pendingDeleteName}</strong>? Any CQL references to this parameter will become invalid.
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingDeleteId(null)}>Cancel</Button>
          <Button
            color="error"
            onClick={() => { if (pendingDeleteId) handleRemove(pendingDeleteId); setPendingDeleteId(null) }}
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
