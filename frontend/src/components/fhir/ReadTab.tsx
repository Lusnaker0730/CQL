import { useState } from 'react'
import {
  Stack,
  TextField,
  CircularProgress,
  Alert,
} from '@mui/material'
import { Search as SearchIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useMutation } from '@tanstack/react-query'
import { fhirApi } from '../../api'
import ResourceDetailDialog from './ResourceDetailDialog'

interface ReadTabProps {
  fhirServer: string
  resourceType: string
}

export default function ReadTab({ fhirServer, resourceType }: ReadTabProps) {
  const [resourceId, setResourceId] = useState('')
  const [readResult, setReadResult] = useState<object | null>(null)
  const [detailOpen, setDetailOpen] = useState(false)

  const readMutation = useMutation({
    mutationFn: () => fhirApi.read(resourceType, resourceId, fhirServer),
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
        label="Resource ID"
        value={resourceId}
        onChange={(e) => setResourceId(e.target.value)}
        onKeyDown={handleKeyDown}
        size="small"
        fullWidth
        placeholder="e.g., example-patient-1"
      />

      <GradientButton
        onClick={handleRead}
        disabled={readMutation.isPending || !resourceId}
        startIcon={readMutation.isPending ? <CircularProgress size={20} color="inherit" /> : <SearchIcon />}
        sx={{ '&.Mui-disabled': { background: 'rgba(0,0,0,0.12)' } }}
      >
        {readMutation.isPending ? 'Loading...' : 'Read Resource'}
      </GradientButton>

      {readMutation.isError && (
        <Alert severity="error">
          Read failed: {(readMutation.error as Error).message}
        </Alert>
      )}

      <ResourceDetailDialog
        open={detailOpen}
        resource={readResult}
        resourceType={resourceType}
        resourceId={resourceId}
        fhirServer={fhirServer}
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
