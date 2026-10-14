import { useTranslation } from 'react-i18next'
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Button,
  Chip,
  Link,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { ExpandMore as ExpandMoreIcon, PushPin as PinIcon, PushPinOutlined as UnpinIcon } from '@mui/icons-material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { measureApi } from '../../api'
import { useNotification } from '../../hooks/useNotification'
import { extractApiError } from '../../utils/errorUtils'
import { STALE_30S } from '../../constants/queryConstants'
import type { MeasureTerminology, MeasureTerminologyItem } from '../../types'

interface Props {
  measureId: number
  /** Pins are measure logic: outside draft (or under someone else's lock) nothing here is editable. */
  logicLocked?: boolean
}

const terminologyQueryKey = (measureId: number) => ['measures', measureId, 'terminology'] as const

/**
 * PAT-260 — the value sets the measure's CQL references, which version each one evaluates with today, and the
 * pins. A platform value set referenced without a version follows the newest active version at every run;
 * pinning freezes it (the platform's answer to MADiE's manifest pinning). CQL `version '…'` always wins.
 */
export default function MeasureTerminologySection({ measureId, logicLocked }: Props) {
  const { t } = useTranslation('measures')
  const queryClient = useQueryClient()
  const { showNotification } = useNotification()
  const { data, isLoading, isError } = useQuery({
    queryKey: terminologyQueryKey(measureId),
    queryFn: () => measureApi.getTerminology(measureId),
    staleTime: STALE_30S,
  })

  const onDone = (next: MeasureTerminology) => {
    queryClient.setQueryData(terminologyQueryKey(measureId), next)
    queryClient.invalidateQueries({ queryKey: ['measures'] })
  }
  const onFail = (err: unknown) => showNotification(extractApiError(err), 'error')
  const pin = useMutation({ mutationFn: (body?: { url: string; version?: string }) => measureApi.pinTerminology(measureId, body), onSuccess: onDone, onError: onFail })
  const unpin = useMutation({ mutationFn: (url?: string) => measureApi.unpinTerminology(measureId, url), onSuccess: onDone, onError: onFail })

  const items = data?.items ?? []
  const editable = !!data?.pinnable && !logicLocked
  const unpinnedPlatform = items.filter((i) => i.source === 'platform' && !i.pinned)
  const pinnedByMeasure = items.filter((i) => !!i.pinnedVersion)
  const busy = pin.isPending || unpin.isPending

  const resolvedLabel = (item: MeasureTerminologyItem) => {
    if (item.source !== 'platform') return t('details.terminology.external')
    const version = item.resolvedVersion ?? t('details.terminology.unresolved')
    if (item.cqlVersion) return t('details.terminology.resolvedCql', { version })
    if (item.pinnedVersion) return t('details.terminology.resolvedPinned', { version })
    return t('details.terminology.resolvedFloating', { version })
  }

  return (
    <Accordion data-testid="terminology-section">
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography variant="subtitle2">{t('details.terminology.title')}</Typography>
          {items.length > 0 && (
            <Chip
              size="small"
              color={unpinnedPlatform.length > 0 ? 'warning' : 'success'}
              label={unpinnedPlatform.length > 0
                ? t('details.terminology.unpinnedCount', { count: unpinnedPlatform.length })
                : t('details.terminology.allPinned')}
            />
          )}
        </Stack>
      </AccordionSummary>
      <AccordionDetails>
        <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary', mb: 1.5 }}>
          {t('details.terminology.hint')}
        </Typography>
        {isError && <Alert severity="error">{t('details.terminology.loadError')}</Alert>}
        {!isLoading && !isError && items.length === 0 && (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>{t('details.terminology.none')}</Typography>
        )}
        {items.length > 0 && (
          <Table size="small" aria-label={t('details.terminology.title')}>
            <TableHead>
              <TableRow>
                <TableCell>{t('details.terminology.valueSet')}</TableCell>
                <TableCell>{t('details.terminology.resolves')}</TableCell>
                <TableCell align="right">{t('details.terminology.pin')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.url} data-testid={`terminology-row-${item.url}`}>
                  <TableCell>
                    <Typography variant="body2">{item.name || item.url}</Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary', wordBreak: 'break-all' }}>{item.url}</Typography>
                  </TableCell>
                  <TableCell>
                    <Typography variant="body2">{resolvedLabel(item)}</Typography>
                    {item.source === 'platform' && item.versions.length > 1 && (
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {t('details.terminology.versionsHeld', { versions: item.versions.map((v) => `${v.version} (${v.status})`).join(', ') })}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell align="right">
                    {item.source !== 'platform' || item.cqlVersion ? (
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {item.cqlVersion ? t('details.terminology.pinnedInCql') : t('details.terminology.notPinnable')}
                      </Typography>
                    ) : item.pinnedVersion ? (
                      <Button size="small" startIcon={<UnpinIcon />} disabled={!editable || busy} onClick={() => unpin.mutate(item.url)}>
                        {t('details.terminology.unpin')}
                      </Button>
                    ) : (
                      <Button size="small" startIcon={<PinIcon />} disabled={!editable || busy} onClick={() => pin.mutate({ url: item.url })}>
                        {t('details.terminology.pinNow')}
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
        {items.length > 0 && (
          <Stack direction="row" spacing={1} sx={{ mt: 1.5, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <Button size="small" variant="outlined" startIcon={<PinIcon />} disabled={!editable || busy || unpinnedPlatform.length === 0}
              onClick={() => pin.mutate(undefined)}>
              {t('details.terminology.pinAll')}
            </Button>
            <Button size="small" disabled={!editable || busy || pinnedByMeasure.length === 0} onClick={() => unpin.mutate(undefined)}>
              {t('details.terminology.unpinAll')}
            </Button>
            {!editable && data && (
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {data.pinnable ? t('details.terminology.locked') : t('details.terminology.draftOnly')}
              </Typography>
            )}
            <Link href="https://hl7.org/fhir/us/cqfmeasures/" target="_blank" rel="noopener" variant="caption" sx={{ ml: 'auto' }}>
              QM IG
            </Link>
          </Stack>
        )}
      </AccordionDetails>
    </Accordion>
  )
}
