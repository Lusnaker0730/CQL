import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  MenuItem,
  IconButton,
} from '@mui/material'
import { Add as AddIcon, Delete as DeleteIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'

interface FunctionsSectionProps {
  functions: { name: string; context?: string; resultType?: string }[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
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

export default function FunctionsSection({ functions, onInsert, onDelete, onGoTo, onEdit }: FunctionsSectionProps) {
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [name, setName] = useState('')
  const [args, setArgs] = useState<FunctionArg[]>([{ name: '', type: 'String' }])
  const [body, setBody] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

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

  const handleStartEdit = (fn: { name: string }) => {
    setEditingItem(fn.name)
    setName(fn.name)
    setBody('')
    setArgs([{ name: '', type: 'String' }])
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setName('')
    setArgs([{ name: '', type: 'String' }])
    setBody('')
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
      {functions.length > 0 ? (
        functions.map((fn, idx) => (
          <ElementListItem
            key={idx}
            label={`${fn.name}()`}
            secondaryLabel={fn.resultType || undefined}
            onGoTo={() => onGoTo?.(fn.name)}
            onEdit={() => handleStartEdit(fn)}
            onDelete={() => setDeleteTarget(fn.name)}
          />
        ))
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          {t('common.noItemsFound', { type: t('sections.functions').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Function' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label={t('functions.name')}
            value={name}
            onChange={(e) => setName(e.target.value)}
          />

          <Typography variant="caption" color="text.secondary">{t('functions.arguments')}</Typography>
          {args.map((arg, idx) => (
            <Stack key={idx} direction="row" spacing={0.5} alignItems="center">
              <TextField
                size="small"
                label={t('functions.argName')}
                value={arg.name}
                onChange={(e) => updateArg(idx, 'name', e.target.value)}
                sx={{ flex: 1 }}
              />
              <TextField
                select
                size="small"
                label={t('functions.argType')}
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
            {t('functions.addArgument')}
          </Button>

          <TextField
            size="small"
            label={t('functions.body')}
            multiline
            rows={3}
            value={body}
            onChange={(e) => setBody(e.target.value)}
            sx={{ '& textarea': { fontFamily: 'monospace', fontSize: '0.8rem' } }}
          />

          {previewSnippet ? (
            <SnippetPreview
              snippet={previewSnippet}
              onInsert={handleConfirmInsert}
              onCancel={() => setPreviewSnippet('')}
              insertLabel={editingItem ? t('common.update') : t('common.insert')}
            />
          ) : (
            <Stack direction="row" spacing={1}>
              <Button size="small" variant="outlined" onClick={handleAdd}
                disabled={!name.trim() || !body.trim()}>
                {editingItem ? t('common.previewUpdate') : t('common.previewInsert')}
              </Button>
              <Button size="small" onClick={resetForm}>{t('common.cancel')}</Button>
            </Stack>
          )}
        </Stack>
      )}

      <ConfirmDeleteDialog
        open={!!deleteTarget}
        title={t('common.deleteElement')}
        itemName={deleteTarget || ''}
        message={t('common.deleteConfirm', { name: deleteTarget })}
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleConfirmDelete}
      />
    </Stack>
  )
}
