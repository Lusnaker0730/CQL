import { Alert } from '@mui/material'
import type { SxProps, Theme } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { ValueSetSearchResponse } from '../../types'

interface TerminologyDegradedHintProps {
  /** The value set search answer; nothing is rendered unless it was answered without the remote terminology server. */
  search?: ValueSetSearchResponse
  sx?: SxProps<Theme>
}

/**
 * PAT-275 — tells the user that the public terminology server is temporarily unavailable (BUG-154 header).
 * In degraded mode the remote server contributed nothing, so any `remote` entry came from the VSAC fallback:
 * with VSAC entries the results are "possibly incomplete", without them only this installation's own value sets
 * are listed. Information only: the results stay selectable.
 */
export default function TerminologyDegradedHint({ search, sx }: TerminologyDegradedHintProps) {
  const { t } = useTranslation('terminology')
  if (!search?.terminologyDegraded) return null
  const vsacToo = search.valueSets.some((vs) => vs.source === 'remote')
  return (
    <Alert severity="warning" variant="outlined" sx={sx}>
      {t(vsacToo ? 'valueSet.remoteDegradedPartial' : 'valueSet.remoteDegraded')}
    </Alert>
  )
}
