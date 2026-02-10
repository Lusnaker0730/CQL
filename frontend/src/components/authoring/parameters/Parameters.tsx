import {
  Box, Stack, Typography, IconButton, Tooltip, TextField, Card, CardContent,
  MenuItem, Select, FormControl, InputLabel,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../../common/GradientButton'
import type { Parameter } from '../../../types/authoring'

function generateId(): string {
  return crypto.randomUUID ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`
}

const PARAMETER_TYPES = [
  { value: 'boolean', label: 'Boolean' },
  { value: 'integer', label: 'Integer' },
  { value: 'decimal', label: 'Decimal' },
  { value: 'string', label: 'String' },
  { value: 'datetime', label: 'DateTime' },
  { value: 'time', label: 'Time' },
  { value: 'interval<integer>', label: 'Interval<Integer>' },
  { value: 'interval<datetime>', label: 'Interval<DateTime>' },
]

interface ParametersProps {
  parameters: Parameter[]
  onChange: (parameters: Parameter[]) => void
}

export default function Parameters({ parameters, onChange }: ParametersProps) {
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
                  />
                  <FormControl size="small" sx={{ minWidth: 160 }}>
                    <InputLabel>Type</InputLabel>
                    <Select
                      value={param.type}
                      label="Type"
                      onChange={(e) => handleUpdate(param.uniqueId, { type: e.target.value, value: undefined })}
                    >
                      {PARAMETER_TYPES.map((t) => (
                        <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  {renderValueField(param)}
                  <Tooltip title="Remove parameter">
                    <IconButton size="small" color="error" onClick={() => handleRemove(param.uniqueId)}>
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
    </Box>
  )
}
