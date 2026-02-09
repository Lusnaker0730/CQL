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
  Chip,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'

interface DefinitionsSectionProps {
  expressions: { name: string; context?: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
}

const TEMPLATES = [
  { label: 'Blank', template: '' },
  { label: 'Age Filter', template: 'AgeInYearsAt(start of "Measurement Period") >= 18' },
  { label: 'Condition Check', template: 'exists [Condition: "ValueSetName"] C\n    where C.clinicalStatus ~ "active"' },
  { label: 'Encounter Check', template: 'exists [Encounter] E\n    where E.period during "Measurement Period"\n      and E.status = \'finished\'' },
  { label: 'Medication Check', template: 'exists [MedicationRequest] M\n    where M.authoredOn during "Measurement Period"\n      and M.status = \'active\'' },
  { label: 'Observation Value', template: '[Observation: "CodeName"] O\n    where O.effective in "Measurement Period"\n    sort by effective desc' },
]

export default function DefinitionsSection({ expressions, onInsert }: DefinitionsSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [context, setContext] = useState('Patient')
  const [templateIdx, setTemplateIdx] = useState(0)
  const [expression, setExpression] = useState('')

  const handleTemplateChange = (idx: number) => {
    setTemplateIdx(idx)
    setExpression(TEMPLATES[idx].template)
  }

  const handleAdd = () => {
    if (!name.trim() || !expression.trim()) return
    const snippet = `define "${name}":\n  ${expression.split('\n').join('\n  ')}`
    onInsert(snippet)
    resetForm()
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setContext('Patient')
    setTemplateIdx(0)
    setExpression('')
  }

  return (
    <Stack spacing={1}>
      {expressions.length > 0 ? (
        <List dense disablePadding>
          {expressions.map((expr, idx) => (
            <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
              <ListItemText
                primary={
                  <Stack direction="row" spacing={0.5} alignItems="center">
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {expr.name}
                    </Typography>
                    {expr.resultType && (
                      <Chip label={expr.resultType} size="small" sx={{ height: 18, fontSize: '0.65rem' }} />
                    )}
                  </Stack>
                }
              />
            </ListItem>
          ))}
        </List>
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          No definitions found
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          Add Definition
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label="Definition Name"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <TextField
            select
            size="small"
            label="Context"
            value={context}
            onChange={(e) => setContext(e.target.value)}
          >
            <MenuItem value="Patient">Patient</MenuItem>
            <MenuItem value="Population">Population</MenuItem>
          </TextField>

          <TextField
            select
            size="small"
            label="Template"
            value={templateIdx}
            onChange={(e) => handleTemplateChange(Number(e.target.value))}
          >
            {TEMPLATES.map((t, i) => (
              <MenuItem key={i} value={i}>{t.label}</MenuItem>
            ))}
          </TextField>

          <TextField
            size="small"
            label="Expression"
            multiline
            rows={3}
            value={expression}
            onChange={(e) => setExpression(e.target.value)}
            sx={{ '& textarea': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
          />

          <Stack direction="row" spacing={1}>
            <GradientButton onClick={handleAdd}
              disabled={!name.trim() || !expression.trim()}>
              Insert
            </GradientButton>
            <Button size="small" onClick={resetForm}>Cancel</Button>
          </Stack>
        </Stack>
      )}
    </Stack>
  )
}
