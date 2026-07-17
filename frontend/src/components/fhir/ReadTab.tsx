import { useState } from 'react'
import {
  Stack,
  TextField,
  CircularProgress,
  Alert,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import { useTranslation } from 'react-i18next'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import ResourceDetailDialog from './ResourceDetailDialog'

interface ReadTabProps {
  connectionId: number | null
  resourceType: string
}

export default function ReadTab({ connectionId, resourceType }: ReadTabProps) {
  const { t } = useTranslation('fhir')
  const [resourceId, setResourceId] = useState('')
  const [readResult, setReadResult] = useState<object | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const readMutation = useMutation({
    mutationFn: () => fhirApi.read(resourceType, resourceId, connectionId),
    onSuccess: (data) => {
      setReadResult(data as object)
      setDetailOpen(true)
    },
  })

  const handleRead = () => {
    if (resourceId) {
      readMutation.mutate()
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && resourceId) {
      handleRead()
    }
  }

  return (
    <Stack spacing={2}>
      <TextField
        label={t('read.resourceIdLabel')}
        value={resourceId}
        onChange={(e) => setResourceId(e.target.value)}
        onKeyDown={handleKeyDown}
        size="small"
        fullWidth
        placeholder={t('read.resourceIdPlaceholder')}
      />

      <GradientButton
        onClick={handleRead}
        disabled={readMutation.isPending || !resourceId}
        startIcon={readMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
        sx={{ '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
      >
        {readMutation.isPending ? t('read.loading') : t('read.readButton')}
      </GradientButton>

      {readMutation.isError && (
        <Alert severity="error">
          {t('read.readFailed', { error: (readMutation.error as Error).message })}
        </Alert>
      )}

      <ResourceDetailDialog
        open={detailOpen}
        resource={readResult}
        resourceType={resourceType}
        resourceId={resourceId}
        connectionId={connectionId}
        onClose={() => setDetailOpen(false)}
        onDeleted={() => {
          setReadResult(null)
          setDetailOpen(false)
        }}
        onUpdated={() => {
          setDetailOpen(false)
          readMutation.mutate()
        }}
      />
    </Stack>
  )
}
