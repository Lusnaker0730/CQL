import { useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import {
  Box,
  Button,
  Card,
  CardActionArea,
  CardContent,
  Container,
  Stack,
  Typography,
} from '@mui/material'
import MenuBookIcon from '@mui/icons-material/MenuBook'
import LaunchIcon from '@mui/icons-material/Launch'
import { Helmet } from 'react-helmet-async'
import PublicLayout from '../components/common/PublicLayout'

type LinkTarget = { labelKey: string; route?: string; href?: string }

/**
 * PAT-210 — public documentation hub for the CQL Platform product (how to use the platform:
 * apply, onboard, import data, key features, integration). Complements /learn, which teaches
 * the CQL language itself. No auth required.
 */
export default function DocsPage() {
  const navigate = useNavigate()
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()

  const steps: string[] = ['apply', 'onboard', 'import', 'build']
  const features: { key: string; target: LinkTarget }[] = [
    { key: 'builder', target: { labelKey: 'docs.open.learn', route: '/learn' } },
    { key: 'measures', target: { labelKey: 'docs.open.learn', route: '/learn' } },
    { key: 'cds', target: { labelKey: 'docs.open.templates', route: '/templates' } },
    { key: 'fhirImport', target: { labelKey: 'docs.open.templates', route: '/templates' } },
    { key: 'patientGen', target: { labelKey: 'docs.open.templates', route: '/templates' } },
    { key: 'terminology', target: { labelKey: 'docs.open.learn', route: '/learn' } },
  ]
  const integrations: string[] = ['cdsHooks', 'apiKey', 'fhirBundle']
  const resources: LinkTarget[] = [
    { labelKey: 'docs.resources.learn', route: '/learn' },
    { labelKey: 'docs.resources.templates', route: '/templates' },
    { labelKey: 'docs.resources.status', route: '/status' },
    { labelKey: 'docs.resources.cqlSpec', href: 'https://cql.hl7.org/' },
    { labelKey: 'docs.resources.twcore', href: 'https://twcore.mohw.gov.tw/ig/twcore/' },
    { labelKey: 'docs.resources.cdsHooks', href: 'https://cds-hooks.org/' },
  ]

  const go = (target: LinkTarget) => {
    if (target.route) navigate(target.route)
    else if (target.href) window.open(target.href, '_blank', 'noopener,noreferrer')
  }

  return (
    <>
      <Helmet>
        <title>{t('docs.pageTitle')} — {tc('app.title')}</title>
        <meta name="description" content={t('docs.subtitle')} />
      </Helmet>
      <PublicLayout
        icon={<MenuBookIcon sx={{ color: 'common.white', fontSize: 22 }} />}
        title={t('docs.pageTitle')}
        showApplyCta
      >
        <Container maxWidth="lg" sx={{ py: 4 }}>
          <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>
            {t('docs.title')}
          </Typography>
          <Typography variant="body1" color="text.secondary" sx={{ mb: 4 }}>
            {t('docs.subtitle')}
          </Typography>

          {/* Getting started */}
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            {t('docs.gettingStarted.title')}
          </Typography>
          <Stack spacing={1.5} sx={{ mb: 4 }}>
            {steps.map((s, i) => (
              <Box key={s} sx={{ display: 'flex', gap: 1.5, alignItems: 'flex-start' }}>
                <Box
                  sx={{
                    flexShrink: 0,
                    width: 28,
                    height: 28,
                    borderRadius: '50%',
                    bgcolor: 'primary.main',
                    color: 'primary.contrastText',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    fontWeight: 700,
                    fontSize: 14,
                  }}
                >
                  {i + 1}
                </Box>
                <Box>
                  <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                    {t(`docs.gettingStarted.steps.${s}.title`)}
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t(`docs.gettingStarted.steps.${s}.desc`)}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Stack>

          {/* Features */}
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            {t('docs.features.title')}
          </Typography>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr', md: '1fr 1fr 1fr' }, gap: 2, mb: 4 }}>
            {features.map((f) => (
              <Card key={f.key} variant="outlined">
                <CardActionArea onClick={() => go(f.target)} sx={{ height: '100%' }}>
                  <CardContent>
                    <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5 }}>
                      {t(`docs.features.items.${f.key}.title`)}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      {t(`docs.features.items.${f.key}.desc`)}
                    </Typography>
                  </CardContent>
                </CardActionArea>
              </Card>
            ))}
          </Box>

          {/* Integration */}
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            {t('docs.integration.title')}
          </Typography>
          <Stack spacing={1.5} sx={{ mb: 4 }}>
            {integrations.map((k) => (
              <Box key={k}>
                <Typography variant="subtitle2" sx={{ fontWeight: 600 }}>
                  {t(`docs.integration.items.${k}.title`)}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {t(`docs.integration.items.${k}.desc`)}
                </Typography>
              </Box>
            ))}
          </Stack>

          {/* Resources */}
          <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
            {t('docs.resources.title')}
          </Typography>
          <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', gap: 1.5 }}>
            {resources.map((r) => (
              <Button
                key={r.labelKey}
                variant="outlined"
                size="small"
                endIcon={r.href ? <LaunchIcon fontSize="small" /> : undefined}
                onClick={() => go(r)}
                sx={{ textTransform: 'none' }}
              >
                {t(r.labelKey)}
              </Button>
            ))}
          </Stack>
        </Container>
      </PublicLayout>
    </>
  )
}
