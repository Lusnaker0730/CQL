import { Alert } from '@mui/material'
import type { SxProps, Theme } from '@mui/material'
import { useTranslation } from 'react-i18next'

interface TerminologyDegradedHintProps {
  /** true when the last value set search was answered without the remote terminology server (BUG-154 header). */
  show: boolean
  sx?: SxProps<Theme>
}

/**
 * PAT-275 — tells the user that only this installation's own value sets are listed because the public
 * terminology server is temporarily unavailable. Information only: the results stay selectable.
 */
export default function TerminologyDegradedHint({ show, sx }: TerminologyDegradedHintProps) {
  const { t } = useTranslation('terminology')
  if (!show) return null
  return (
    <Alert severity="warning" variant="outlined" sx={sx}>
      {t('valueSet.remoteDegraded')}
    </Alert>
  )
}
