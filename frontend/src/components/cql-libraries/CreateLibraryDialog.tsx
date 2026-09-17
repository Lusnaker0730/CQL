import { useState, useMemo, useCallback } from 'react'
import { useTranslation } from 'react-i18next'
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Button,
  TextField,
  Stack,
  Alert,
} from '@mui/material'
import { useCreateCqlLibrary } from '../../hooks/useCqlLibraries'
import { extractApiError } from '../../utils/errorUtils'
import type { CqlLibrary } from '../../types'

const CQL_NAME_REGEX = /^[A-Z][a-zA-Z0-9_]*$/
const NAME_MAX_LENGTH = 64

function buildCqlTemplate(name: string): string {
  const safeName = CQL_NAME_REGEX.test(name) ? name : 'MyLibrary'
  return `library ${safeName} version '1.0.0'

using FHIR version '4.0.1'

include FHIRHelpers version '4.0.1' called FHIRHelpers
`
}

interface CreateLibraryDialogProps {
  open: boolean
  onClose: () => void
  onCreated?: (library: CqlLibrary) => void
}

export default function CreateLibraryDialog({ open, onClose, onCreated }: CreateLibraryDialogProps) {
  const { t } = useTranslation('cqlLibraries')

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [cqlContent, setCqlContent] = useState('')
  const [nameTouched, setNameTouched] = useState(false)

  const createMutation = useCreateCqlLibrary()

  const nameError = useMemo(() => {
    if (!nameTouched) return ''
    if (!name.trim()) return t('create.nameError')
    if (!CQL_NAME_REGEX.test(name)) return t('create.nameError')
    if (name.length > NAME_MAX_LENGTH) return t('create.nameError')
    return ''
  }, [name, nameTouched, t])

  const isValid = useMemo(
    () => name.trim().length > 0 && CQL_NAME_REGEX.test(name) && name.length <= NAME_MAX_LENGTH,
    [name],
  )

  // Auto-update CQL template when the name changes and template hasn't been manually edited
  const defaultTemplate = useMemo(() => buildCqlTemplate(name || 'MyLibrary'), [name])

  const handleNameChange = useCallback(
    (newName: string) => {
      const prevDefault = buildCqlTemplate(name || 'MyLibrary')
      setName(newName)
      // Auto-sync the CQL content if it matches the previous template or is empty
      if (!cqlContent || cqlContent === prevDefault) {
        setCqlContent(buildCqlTemplate(newName || 'MyLibrary'))
      }
    },
    [name, cqlContent],
  )

  const resetForm = useCallback(() => {
    setName('')
    setDescription('')
    setCqlContent('')
    setNameTouched(false)
    createMutation.reset()
  }, [createMutation])

  const handleClose = useCallback(() => {
    resetForm()
    onClose()
  }, [resetForm, onClose])

  const handleSubmit = useCallback(() => {
    if (!isValid) return

    const finalCql = cqlContent.trim() || defaultTemplate
    createMutation.mutate(
      { cql: finalCql, description: description.trim() || undefined },
      {
        onSuccess: (library) => {
          onCreated?.(library)
          handleClose()
        },
      },
    )
  }, [isValid, cqlContent, defaultTemplate, description, createMutation, onCreated, handleClose])

  return (
    <Dialog open={open} onClose={handleClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t('create.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {/* Library Name */}
          <TextField
            label={t('create.name')}
            required
            fullWidth
            autoFocus
            size="small"
            value={name}
            onChange={(e) => handleNameChange(e.target.value)}
            onBlur={() => setNameTouched(true)}
            error={!!nameError}
            helperText={nameError || t('create.nameHint')}
            slotProps={{
              htmlInput: { maxLength: NAME_MAX_LENGTH }
            }}
          />

          {/* Description */}
          <TextField
            label={t('create.description')}
            fullWidth
            size="small"
            multiline
            rows={2}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            helperText={t('create.descriptionHint')}
          />

          {/* Initial CQL Content */}
          <TextField
            label={t('create.cqlContent')}
            fullWidth
            size="small"
            multiline
            rows={6}
            value={cqlContent || defaultTemplate}
            onChange={(e) => setCqlContent(e.target.value)}
            slotProps={{
              input: {
                sx: { fontFamily: '"Consolas", "Monaco", monospace', fontSize: '0.85rem' },
              }
            }}
          />

          {/* Mutation error */}
          {createMutation.isError && (
            <Alert severity="error">
              {t('errors.createFailed', { error: extractApiError(createMutation.error) })}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose}>{t('common:actions.cancel')}</Button>
        <Button
          onClick={handleSubmit}
          variant="contained"
          disabled={!isValid || createMutation.isPending}
        >
          {createMutation.isPending ? t('create.creating') : t('create.create')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
