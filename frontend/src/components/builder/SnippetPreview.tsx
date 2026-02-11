import { Box, Stack, Button, IconButton, Tooltip } from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import { useNotification } from '../../hooks/useNotification'

interface SnippetPreviewProps {
  snippet: string
  onInsert: () => void
  onCancel: () => void
  insertLabel?: string
  insertDisabled?: boolean
}

export default function SnippetPreview({
  snippet,
  onInsert,
  onCancel,
  insertLabel = 'Insert',
  insertDisabled = false,
}: SnippetPreviewProps) {
  const { showNotification } = useNotification()

  if (!snippet) return null

  const handleCopy = async () => {
    try {
      await navigator.clipboard.writeText(snippet)
      showNotification('Copied to clipboard', 'success', 2000)
    } catch {
      showNotification('Failed to copy', 'error', 2000)
    }
  }

  return (
    <Stack spacing={0.75}>
      <Box
        sx={{
          p: 1,
          bgcolor: 'rgba(27,58,92,0.04)',
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 1,
          fontFamily: 'monospace',
          fontSize: '0.75rem',
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-all',
          maxHeight: 120,
          overflow: 'auto',
        }}
      >
        {snippet}
      </Box>
      <Stack direction="row" spacing={1} alignItems="center">
        <GradientButton onClick={onInsert} disabled={insertDisabled}>
          {insertLabel}
        </GradientButton>
        <Tooltip title="Copy to clipboard">
          <IconButton size="small" onClick={handleCopy}>
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Button size="small" onClick={onCancel}>Cancel</Button>
      </Stack>
    </Stack>
  )
}
