import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  IconButton,
  List,
  ListItem,
  ListItemText,
  Chip,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'

interface FunctionsSectionProps {
  functions: { name: string; context?: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
}

interface FunctionArg {
  name: string
  type: string
}

const ARG_TYPES = [
  'Boolean', 'Integer', 'Decimal', 'String', 'DateTime', 'Date',
  'Code', 'Concept', 'Quantity', 'FHIR.Patient', 'FHIR.Condition',
  'FHIR.Observation', 'FHIR.Encounter', 'FHIR.MedicationRequest',
  'List<FHIR.Condition>', 'List<FHIR.Observation>',
]

export default function FunctionsSection({ functions, onInsert }: FunctionsSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [args, setArgs] = useState<FunctionArg[]>([{ name: '', type: 'String' }])
  const [body, setBody] = useState('')

  const addArg = () => {
    setArgs((prev) => [...prev, { name: '', type: 'String' }])
  }

  const updateArg = (idx: number, field: keyof FunctionArg, value: string) => {
    setArgs((prev) => {
      const updated = [...prev]
      updated[idx] = { ...updated[idx], [field]: value }
      return updated
    })
  }

  const removeArg = (idx: number) => {
    setArgs((prev) => prev.filter((_, i) => i !== idx))
  }

  const handleAdd = () => {
    if (!name.trim() || !body.trim()) return
    const argList = args
      .filter((a) => a.name.trim())
      .map((a) => `${a.name} ${a.type}`)
      .join(', ')
    const snippet = `define function "${name}"(${argList}):\n  ${body.split('\n').join('\n  ')}`
    onInsert(snippet)
    resetForm()
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setArgs([{ name: '', type: 'String' }])
    setBody('')
  }

  return (
    <Stack spacing={1}>
      {functions.length > 0 ? (
        <List dense disablePadding>
          {functions.map((fn, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {fn.name}()
                    </Typography>
                    {fn.resultType && (
                      <Chip label={fn.resultType} size="small" sx={{ height: 18, fontSize: '0.65rem' }} />
                    )}
                  </Stack>
                }
              />
            </ListItem>
          ))}
        </List>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No functions found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add Function
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label="Function Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <Typography variant="caption" color="text.secondary">Arguments</Typography>
          {args.map((arg, idx) => (
            <Stack key={idx} direction="row" spacing={0.5} alignItems="center">
              <TextField
                size="small"
                label="Name"
                value={arg.name}
                onChange={(e) => updateArg(idx, 'name', e.target.value)}
                sx={{ flex: 1 }}
              />
              <TextField
                select
                size="small"
                label="Type"
                value={arg.type}
                onChange={(e) => updateArg(idx, 'type', e.target.value)}
                sx={{ flex: 1 }}
              >
                {ARG_TYPES.map((t) => (
                  <MenuItem key={t} value={t}>{t}</MenuItem>
                ))}
              </TextField>
              {args.length > 1 && (
                <IconButton size="small" onClick={() => removeArg(idx)} aria-label="Remove argument">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              )}
            </Stack>
          ))}
          <Button size="small" startIcon={<AddIcon />} onClick={addArg} sx={{ alignSelf: 'flex-start' }}>
            Add Argument
          </Button>

          <TextField
            size="small"
            label="Function Body"
            multiline
            rows={3}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            sx={{ '& textarea': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
          />

          <Stack direction="row" spacing={1}>
            <GradientButton onClick={handleAdd}
              disabled={!name.trim() || !body.trim()}>
              Insert
            </GradientButton>
            <Button size="small" onClick={resetForm}>Cancel</Button>
          </Stack>
        </Stack>
      )}
    </Stack>
  )
}
