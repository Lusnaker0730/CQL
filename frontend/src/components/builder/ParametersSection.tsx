import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  List,
  ListItem,
  ListItemText,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'

interface ParametersSectionProps {
  parameters: string[]
  onInsert: (cqlSnippet: string) => void
}

const CQL_TYPES = [
  'Boolean',
  'Integer',
  'Decimal',
  'String',
  'DateTime',
  'Date',
  'Time',
  'Quantity',
  'Code',
  'Concept',
  'Interval<DateTime>',
  'Interval<Date>',
  'Interval<Integer>',
  'Interval<Decimal>',
  'Interval<Quantity>',
  'List<String>',
  'List<Code>',
]

export default function ParametersSection({ parameters, onInsert }: ParametersSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [paramType, setParamType] = useState('Boolean')
  const [defaultValue, setDefaultValue] = useState('')

  const handleAdd = () => {
    if (!name.trim()) return
    let snippet = `parameter "${name}" ${paramType}`
    if (defaultValue.trim()) {
      snippet += ` default ${defaultValue}`
    }
    onInsert(snippet)
    resetForm()
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setParamType('Boolean')
    setDefaultValue('')
  }

  return (
    <Stack spacing={1}>
      {parameters.length > 0 ? (
        <List dense disablePadding>
          {parameters.map((param, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                    {param}
                  </Typography>
                }
              />
            </ListItem>
          ))}
        </List>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No parameters found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add Parameter
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label="Parameter Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <TextField
            select
            size="small"
            label="Type"
            value={paramType}
            onChange={(e) => setParamType(e.target.value)}
          >
            {CQL_TYPES.map((t) => (
              <MenuItem key={t} value={t}>{t}</MenuItem>
            ))}
          </TextField>

          <TextField
            size="small"
            label="Default Value (optional)"
            value={defaultValue}
            onChange={(e) => setDefaultValue(e.target.value)}
            placeholder="e.g. true, 42, @2024-01-01"
          />

          <Stack direction="row" spacing={1}>
            <GradientButton onClick={handleAdd} disabled={!name.trim()}>
              Insert
            </GradientButton>
            <Button size="small" onClick={resetForm}>Cancel</Button>
          </Stack>
        </Stack>
      )}
    </Stack>
  )
}
