import { useState } from 'react'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'

interface ParametersSectionProps {
  parameters: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
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

/**
 * Parse a parameter string like: "Measurement Period" Interval<DateTime>
 *   default Interval[@2024-01-01T00:00:00.0, @2024-12-31T23:59:59.999]
 */
function parseParameter(raw: string): { name: string; type: string; defaultValue: string } | null {
  const m = raw.match(/^"([^"]+)"\s+(\S+)(?:\s+default\s+(.+))?/)
  if (m) return { name: m[1], type: m[2], defaultValue: m[3] || '' }
  return null
}

export default function ParametersSection({ parameters, onInsert, onDelete, onGoTo, onEdit }: ParametersSectionProps) {
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [paramType, setParamType] = useState('Boolean')
  const [defaultValue, setDefaultValue] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const handleAdd = () => {
    if (!name.trim()) return
    let snippet = `parameter "${name}" ${paramType}`
    if (defaultValue.trim()) {
      snippet += `\n  default ${defaultValue}`
    }
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

  const handleStartEdit = (raw: string) => {
    const parsed = parseParameter(raw)
    if (!parsed) return
    setEditingItem(parsed.name)
    setName(parsed.name)
    setParamType(CQL_TYPES.includes(parsed.type) ? parsed.type : 'Boolean')
    setDefaultValue(parsed.defaultValue)
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setParamType('Boolean')
    setDefaultValue('')
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
      {parameters.length > 0 ? (
        parameters.map((param, idx) => {
          const parsed = parseParameter(param)
          const name = parsed?.name || param
          return (
            <ElementListItem
              key={idx}
              label={name}
              secondaryLabel={parsed?.type}
              onGoTo={() => onGoTo?.(name)}
              onEdit={() => handleStartEdit(param)}
              onDelete={() => setDeleteTarget(name)}
            />
          )
        })
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

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? 'Update' : 'Insert'}
            />
          ) : (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd} disabled={!name.trim()}>
                Preview {editingItem ? 'Update' : 'Insert'}
              </Button>
              <Button size="small" onClick={resetForm}>Cancel</Button>
            </Stack>
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
