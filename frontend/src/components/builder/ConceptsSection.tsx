import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Stack,
  TextField,
  Button,
  Typography,
  Checkbox,
  FormControlLabel,
  Box,
} from '@mui/material'
import { Add as AddIcon } from '@mui/icons-material'
import ElementListItem from './ElementListItem'
import ConfirmDeleteDialog from '../common/ConfirmDeleteDialog'
import SnippetPreview from './SnippetPreview'

interface ConceptsSectionProps {
  concepts: string[]
  codes: string[]
  onInsert: (cqlSnippet: string) => void
  onDelete?: (identifier: string) => void
  onGoTo?: (identifier: string) => void
  onEdit?: (identifier: string, newSnippet: string) => void
}

/**
 * Parse a code string to extract just the name.
 * Input: "HbA1c Code": '4548-4' from "LOINC" display 'Hemoglobin A1c'
 * Output: "HbA1c Code"
 */
function parseCodeName(raw: string): string {
  const m = raw.match(/^"([^"]+)"/)
  return m ? m[1] : raw
}

export default function ConceptsSection({
  concepts,
  codes,
  onInsert,
  onDelete,
  onGoTo,
  onEdit,
}: ConceptsSectionProps) {
  const { t } = useTranslation('builder')
  const [showForm, setShowForm] = useState(false)
  const [conceptName, setConceptName] = useState('')
  const [selectedCodes, setSelectedCodes] = useState<string[]>([])
  const [displayText, setDisplayText] = useState('')
  const [editingItem, setEditingItem] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<string | null>(null)
  const [previewSnippet, setPreviewSnippet] = useState('')

  const codeNames = codes.map(parseCodeName)

  const handleToggleCode = (codeName: string) => {
    setSelectedCodes((prev) =>
      prev.includes(codeName)
        ? prev.filter((c) => c !== codeName)
        : [...prev, codeName]
    )
  }

  const handleAdd = () => {
    if (!conceptName.trim() || selectedCodes.length === 0) return
    const codeRefs = selectedCodes.map((c) => `"${c}"`).join(', ')
    const displayPart = displayText.trim() ? ` display '${displayText.trim()}'` : ''
    const snippet = `concept "${conceptName}": { ${codeRefs} }${displayPart}`
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

  const handleStartEdit = (name: string) => {
    setEditingItem(name)
    setConceptName(name)
    setSelectedCodes([])
    setDisplayText('')
    setShowForm(true)
  }

  const resetForm = () => {
    setShowForm(false)
    setConceptName('')
    setSelectedCodes([])
    setDisplayText('')
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
      {concepts.length > 0 ? (
        concepts.map((concept, idx) => (
          <ElementListItem
            key={idx}
            label={concept}
            onGoTo={() => onGoTo?.(concept)}
            onEdit={() => handleStartEdit(concept)}
            onDelete={() => setDeleteTarget(concept)}
          />
        ))
      ) : (
        <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic' }}>
          {t('common.noItemsFound', { type: t('sections.concepts').toLowerCase() })}
        </Typography>
      )}

      {!showForm ? (
        <Button size="small" startIcon={<AddIcon />} onClick={() => setShowForm(true)} sx={{ alignSelf: 'flex-start' }}>
          {t('common.addItem', { type: 'Concept' })}
        </Button>
      ) : (
        <Stack spacing={1} sx={{ p: 1, bgcolor: 'rgba(13,115,119,0.03)', borderRadius: 1 }}>
          <TextField
            size="small"
            label={t('concepts.name')}
            value={conceptName}
            onChange={(e) => setConceptName(e.target.value)}
          />

          <Typography variant="caption" fontWeight={500} color="text.secondary">
            {t('concepts.selectCodes')}
          </Typography>
          {codeNames.length > 0 ? (
            <Box sx={{ maxHeight: 160, overflow: 'auto', pl: 0.5 }}>
              {codeNames.map((codeName) => (
                <FormControlLabel
                  key={codeName}
                  control={
                    <Checkbox
                      size="small"
                      checked={selectedCodes.includes(codeName)}
                      onChange={() => handleToggleCode(codeName)}
                      sx={{ py: 0.25 }}
                    />
                  }
                  label={
                    <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {codeName}
                    </Typography>
                  }
                  sx={{ display: 'flex', ml: 0 }}
                />
              ))}
            </Box>
          ) : (
            <Typography variant="body2" color="text.secondary" sx={{ fontStyle: 'italic', fontSize: '0.8rem' }}>
              {t('concepts.noCodesAvailable')}
            </Typography>
          )}

          <TextField
            size="small"
            label={t('concepts.display')}
            value={displayText}
            onChange={(e) => setDisplayText(e.target.value)}
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
                disabled={!conceptName.trim() || selectedCodes.length === 0}>
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
