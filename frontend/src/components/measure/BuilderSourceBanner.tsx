import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, Typography } from '@mui/material'
import { Construction as BuilderIcon } from '@mui/icons-material'
import { measureApi } from '../../api'
import { STALE_1M } from '../../constants/queryConstants'

interface Props {
  measureId: number
  /** Changes whenever the measure is saved, so the drift status is re-read. */
  measureUpdatedAt?: string
}

/**
 * PAT-238 — shown on a measure that was published from the eCQM builder: where it came from,
 * a way back to the builder, and whether either side changed since that publish (logic edited
 * here would be overwritten by the next publish — the builder asks first; builder changes are
 * not on this measure until it is published again).
 */
export default function BuilderSourceBanner({ measureId, measureUpdatedAt }: Props) {
  const { t } = useTranslation('measures')
  const navigate = useNavigate()
  const { data: source } = useQuery({
    queryKey: ['measures', measureId, 'builder-source', measureUpdatedAt] as const,
    queryFn: () => measureApi.getBuilderSource(measureId),
    staleTime: STALE_1M,
  })

  if (!source) return null

  const publishedAt = source.publishedAt ? new Date(source.publishedAt).toLocaleString() : null
  const drifted = source.measureEditedSincePublish || source.builderChangedSincePublish

  return (
    <Alert
      severity={drifted ? 'warning' : 'info'}
      icon={<BuilderIcon fontSize="small" />}
      sx={{ borderRadius: 0 }}
      action={
        <Button color="inherit" size="small" onClick={() => navigate(`/ecqm?artifact=${source.artifactId}`)}>
          {t('builderSource.openInBuilder')}
        </Button>
      }
      data-testid="builder-source-banner"
    >
      <Typography variant="body2">
        {t('builderSource.builtIn', { name: source.artifactName, owner: source.ownerUsername })}
        {publishedAt && ` ${t('builderSource.publishedAt', { at: publishedAt })}`}
      </Typography>
      {(source.measureEditedSincePublish || source.builderChangedSincePublish) && (
        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
          {source.measureEditedSincePublish && <li><Typography variant="body2">{t('builderSource.measureEdited')}</Typography></li>}
          {source.builderChangedSincePublish && <li><Typography variant="body2">{t('builderSource.builderChanged')}</Typography></li>}
        </Box>
      )}
    </Alert>
  )
}
