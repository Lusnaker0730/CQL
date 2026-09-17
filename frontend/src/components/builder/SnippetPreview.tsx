import { useTranslation } from 'react-i18next'
import { Stack, Button, IconButton, Tooltip } from '@mui/material'
import { ContentCopy as CopyIcon } from '@mui/icons-material'
import GradientButton from '../common/GradientButton'
import CqlPreviewBox from './CqlPreviewBox'
import { useCopyToClipboard } from '../../hooks/useCopyToClipboard'

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
  const { t } = useTranslation('builder')
  const copyToClipboard = useCopyToClipboard()

  if (!snippet) return null

  return (
    <Stack spacing={0.75}>
      <CqlPreviewBox code={snippet} />
      <Stack direction="row" spacing={1} sx={{
        alignItems: "center"
      }}>
        <GradientButton onClick={onInsert} disabled={insertDisabled}>
          {insertLabel}
        </GradientButton>
        <Tooltip title={t('common.copyToClipboard')}>
          <IconButton size="small" onClick={() => copyToClipboard(snippet)} aria-label={t('common.copyToClipboard')}>
            <CopyIcon fontSize="small" />
          </IconButton>
        </Tooltip>
        <Button size="small" onClick={onCancel}>{t('common.cancel')}</Button>
      </Stack>
    </Stack>
  );
}
