import { useEffect } from 'react'
import { Box, Stack, Button, CircularProgress, Alert } from '@mui/material'
import { Translate as TranslateIcon, Save as SaveIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useSelector, useDispatch } from 'react-redux'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { RootState } from '../../store'
import { setCqlContent } from '../../store/editorSlice'
import CqlEditor from '../editor/CqlEditor'
import { measureApi, cqlApi } from '../../api'
import { useUnsavedChangesGuard } from '../../hooks/useUnsavedChangesGuard'
import type { MeasureDefinition } from '../../types'

interface MeasureCqlTabProps {
  measure: MeasureDefinition
  onMeasureUpdate: (updated: MeasureDefinition) => void
}

export default function MeasureCqlTab({ measure, onMeasureUpdate }: MeasureCqlTabProps) {
  const dispatch = useDispatch()
  const { cqlContent } = useSelector((state: RootState) => state.editor)
  const queryClient = useQueryClient()

  useEffect(() => {
    if (measure.cqlContent) {
      dispatch(setCqlContent(measure.cqlContent))
    }
  }, [measure.id])

  const saveMutation = useMutation({
    mutationFn: () =>
      measureApi.updateMeasure(measure.id!, { ...measure, cqlContent }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['measures'] })
      onMeasureUpdate(updated)
    },
  })

  const translateMutation = useMutation({
    mutationFn: () => cqlApi.translate({ cql: cqlContent }),
  })

  const isDirty = cqlContent !== (measure.cqlContent || '')
  useUnsavedChangesGuard(isDirty)

  return (
    <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={1} sx={{ p: 1, borderBottom: '1px solid', borderColor: 'divider' }}>
        <Button
          size="small"
          variant="outlined"
          startIcon={translateMutation.isPending ? <CircularProgress size={16} /> : <TranslateIcon />}
          disabled={translateMutation.isPending || !cqlContent}
          onClick={() => translateMutation.mutate()}
        >
          Translate
        </Button>
        <GradientButton
          startIcon={<SaveIcon />}
          disabled={!isDirty || saveMutation.isPending}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? 'Saving...' : 'Save CQL'}
        </GradientButton>
        {isDirty && (
          <Alert severity="info" sx={{ py: 0, px: 1, fontSize: '0.75rem' }}>
            Unsaved changes
          </Alert>
        )}
      </Stack>

      {translateMutation.isError && (
        <Alert severity="error" sx={{ mx: 1, mt: 1 }}>
          Translation failed: {(translateMutation.error as Error).message}
        </Alert>
      )}

      <Box sx={{ flex: 1, minHeight: 0 }}>
        <CqlEditor height="100%" />
      </Box>
    </Box>
  )
}
