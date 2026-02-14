import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  ToggleButtonGroup,
  ToggleButton,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'
import RetrieveBuilder from './RetrieveBuilder'
import QueryBuilder from './QueryBuilder'
import OperatorPanel from './OperatorPanel'

interface DefinitionsSectionProps {
  expressions: { name: string; context?: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
  valueSets?: string[]
  codes?: string[]
  parameters?: string[]
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

const TEMPLATES = [
  { label: 'Blank', template: '' },
  { label: 'Age Filter', template: 'AgeInYearsAt(start of "Measurement Period") >= 18' },
  { label: 'Condition Check', template: 'exists [Condition: "ValueSetName"] C\n    where C.clinicalStatus ~ "active"' },
  { label: 'Encounter Check', template: 'exists [Encounter] E\n    where E.period during "Measurement Period"\n      and E.status = \'finished\'' },
  { label: 'Medication Check', template: 'exists [MedicationRequest] M\n    where M.authoredOn during "Measurement Period"\n      and M.status = \'active\'' },
  { label: 'Observation Value', template: '[Observation: "CodeName"] O\n    where O.effective in "Measurement Period"\n    sort by effective desc' },
]

export default function DefinitionsSection({
  expressions,
  onInsert,
  valueSets = [],
  codes = [],
  parameters = [],
  onDelete,
  onGoTo,
  onEdit,
}: DefinitionsSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [mode, setMode] = useState<'template' | 'retrieve' | 'query' | 'operator'>('template')
  const [name, setName] = useState('')
  const [context, setContext] = useState('Patient')
  const [templateIdx, setTemplateIdx] = useState(0)
  const [expression, setExpression] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const handleTemplateChange = (idx: number) => {
    setTemplateIdx(idx)
    setExpression(TEMPLATES[idx].template)
  }

  const handleAdd = () => {
    if (!name.trim() || !expression.trim()) return
    const snippet = `define "${name}":\n  ${expression.split('\n').join('\n  ')}`
    setPreviewSnippet(snippet)
  }

  const handleConfirmInsert = () => {
    if (editingItem) {
      onEdit?.(editingItem, previewSnippet)
    } else {
      onInsert(previewSnippet)
    }
    resetForm()
  }

  const handleStartEdit = (expr: { name: string }) => {
    setEditingItem(expr.name)
    setName(expr.name)
    setExpression('')
    setShowForm(true)
    setMode('template')
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setContext('Patient')
    setTemplateIdx(0)
    setExpression('')
    setEditingItem(null)
    setPreviewSnippet('')
  }

  const handleConfirmDelete = () => {
    if (deleteTarget) {
      onDelete?.(deleteTarget)
      setDeleteTarget(null)
    }
  }

  return (
    <Stack spacing={0.5}>
      {expressions.length > 0 ? (
        expressions.map((expr, idx) => (
          <ElementListItem
            key={idx}
            label={expr.name}
            secondaryLabel={expr.resultType || undefined}
            onGoTo={() => onGoTo?.(expr.name)}
            onEdit={() => handleStartEdit(expr)}
            onDelete={() => setDeleteTarget(expr.name)}
          />
        ))
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
          <ToggleButtonGroup
            size="small"
            exclusive
            value={mode}
            onChange={(_, v) => { if (v) setMode(v) }}
            sx={{ alignSelf: 'flex-start' }}
          >
            <ToggleButton value="template" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              Template
            </ToggleButton>
            <ToggleButton value="retrieve" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              Retrieve
            </ToggleButton>
            <ToggleButton value="query" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              Query
            </ToggleButton>
            <ToggleButton value="operator" sx={{ textTransform: 'none', px: 1.5, py: 0.25 }}>
              Operators
            </ToggleButton>
          </ToggleButtonGroup>

          {mode === 'template' ? (
            <>
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

              {previewSnippet ? (
                <SnippetPreview
                  snippet={previewSnippet}
                  onInsert={handleConfirmInsert}
                  onCancel={() => setPreviewSnippet('')}
                  insertLabel={editingItem ? 'Update' : 'Insert'}
                />
              ) : (
                <Stack direction="row" spacing={1}>
                  <Button size="small" variant="outlined" onClick={handleAdd}
                    disabled={!name.trim() || !expression.trim()}>
                    Preview {editingItem ? 'Update' : 'Insert'}
                  </Button>
                  <Button size="small" onClick={resetForm}>Cancel</Button>
                </Stack>
              )}
            </>
          ) : mode === 'retrieve' ? (
            <RetrieveBuilder
              valueSets={valueSets}
              codes={codes}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : mode === 'query' ? (
            <QueryBuilder
              valueSets={valueSets}
              codes={codes}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          ) : (
            <OperatorPanel
              expressions={expressions.map((e) => e.name)}
              parameters={parameters}
              valueSets={valueSets}
              onInsert={(snippet) => {
                onInsert(snippet)
                resetForm()
              }}
              onCancel={resetForm}
            />
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title="Delete Element"
        itemName={deleteTarget || ''}
        message={`Are you sure you want to delete "${deleteTarget}"? This will remove the corresponding lines from the CQL editor.`}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
