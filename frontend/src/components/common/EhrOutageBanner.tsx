import { Alert, AlertTitle, Button, Stack } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useEhrOutage } from '../../hooks/useEhrOutage'
import type { EhrOutageEntry } from '../../contexts/EhrOutageContext'

/**
 * PAT-110: persistent yellow banner stack shown at the top of the app shell
 * when one or more EHR FHIR servers are degraded. One row per failing
 * connection (deduped by connectionId); count of consecutive failures shown
 * inline so the user can tell transient blip from sustained outage.
 *
 * Deliberately NOT auto-dismissing — unlike the red error snackbar. The user
 * dismisses explicitly once they confirm the upstream is recovered.
 */
export default function EhrOutageBanner() {
  const { t } = useTranslation('fhir')
  const { outages, clearOutage } = useEhrOutage()

  if (outages.length === 0) return null

  return (
    <Stack spacing={1} sx={{ p: 1 }}>
      {outages.map((o) => (
        <OutageRow key={o.key} outage={o} t={t} onDismiss={() => clearOutage(o.key)} />
      ))}
    </Stack>
  )
}

function OutageRow({
  outage,
  t,
  onDismiss,
}: {
  outage: EhrOutageEntry
  t: ReturnType<typeof useTranslation>['t']
  onDismiss: () => void
}) {
  const reasonKey = `degradation.reason.${outage.reason}`
  const title = outage.connectionName
    ? t('degradation.titleWithName', { name: outage.connectionName })
    : t('degradation.titleGeneric')

  return (
    <Alert
      severity="warning"
      onClose={onDismiss}
      action={
        <Button size="small" color="inherit" onClick={onDismiss}>
          {t('degradation.dismiss')}
        </Button>
      }
    >
      <AlertTitle>{title}</AlertTitle>
      {t(reasonKey)}
      {outage.failureCount > 1 && (
        <>
          {' '}
          <strong>
            ({t('degradation.failureCount', { count: outage.failureCount })})
          </strong>
        </>
      )}
    </Alert>
  )
}
