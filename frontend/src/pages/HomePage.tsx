import { Accordion, AccordionDetails, AccordionSummary, Box, Button, Chip, Container, Link, Paper, Stack, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  ExpandMore as ExpandMoreIcon,
  Handyman as MaintainIcon,
  Gavel as StandardsIcon,
  VolunteerActivism as FreeIcon,
  Lock as DataIcon2,
  LocalHospital as MedicalIcon,
  School as LearnIcon,
  Login as LoginIcon,
  CloudUpload as ImportIcon,
  AccountTree as BuildIcon,
  FactCheck as VerifyIcon,
  Assessment as RunIcon,
  Code as AuthoringIcon,
  VerifiedUser as VerificationIcon,
  SwapHoriz as ExchangeIcon,
  Storage as DataIcon,
  Security as GovernanceIcon,
  Business as HospitalIcon,
  MedicalServices as ClinicIcon,
  Terminal as DeveloperIcon,
} from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { useTranslation } from 'react-i18next'
import LanguageMenu from '../components/common/LanguageMenu'
import PublicFooter from '../components/common/PublicFooter'
import CqlShowcase from '../components/landing/CqlShowcase'

import type { Theme } from '@mui/material/styles'

/** Same stops as the login page's hero (see theme.ts) so the two public pages read as one site. */
const heroGradient = (theme: Theme) =>
  `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 50%, ${theme.palette.secondary.dark} 100%)`

/** PAT-251: the public home page — what the platform is, for whom, how it works, what it offers. */
const STEPS = [
  { key: 'import', icon: ImportIcon, image: '/screenshots/step-import.jpg', color: '#E8A838' },
  { key: 'build', icon: BuildIcon, image: '/screenshots/step-build.jpg', color: '#0D7377' },
  { key: 'verify', icon: VerifyIcon, image: '/screenshots/step-verify.jpg', color: '#14A3A8' },
  { key: 'run', icon: RunIcon, image: '/screenshots/step-run.jpg', color: '#1B3A5C' },
] as const

const AUDIENCES = [
  { key: 'hospital', icon: HospitalIcon, color: '#1B3A5C' },
  { key: 'clinic', icon: ClinicIcon, color: '#0D7377' },
  { key: 'developer', icon: DeveloperIcon, color: '#E8A838' },
] as const

const CAPABILITY_GROUPS = [
  { key: 'authoring', icon: AuthoringIcon, color: '#0D7377', items: ['builder', 'editor', 'templates', 'libraries'] },
  { key: 'verification', icon: VerificationIcon, color: '#14A3A8', items: ['testCases', 'validation', 'coverage', 'approval'] },
  { key: 'exchange', icon: ExchangeIcon, color: '#1B3A5C', items: ['cqfmPackage', 'madieTestCases', 'fhirReports', 'cdsHooks'] },
  { key: 'data', icon: DataIcon, color: '#E8A838', items: ['myHealthBank', 'ehr', 'patientGenerator', 'terminology'] },
  { key: 'governance', icon: GovernanceIcon, color: '#5C6B7A', items: ['tenants', 'approvalFlow', 'audit', 'apiKeys'] },
] as const

const DEMO_SHOTS = [
  { key: 'measures', image: '/screenshots/demo-measures.jpg' },
  { key: 'report', image: '/screenshots/demo-report.jpg' },
] as const

const STANDARDS = ['HL7 FHIR R4', 'TW Core IG', 'HL7 CQL', 'CQF Measures IG', 'CDS Hooks', 'MADiE-compatible test cases']

const GITHUB_URL = 'https://github.com/Lusnaker0730/CQL'
const TWCORE_URL = 'https://twcore.mohw.gov.tw/ig/twcore/'

/** PAT-251 trust section: who maintains it, which standards it follows, what it costs, where the data lives. */
const TRUST = [
  { key: 'maintenance', icon: MaintainIcon, color: '#1B3A5C', href: GITHUB_URL },
  { key: 'standards', icon: StandardsIcon, color: '#0D7377', href: TWCORE_URL },
  { key: 'pricing', icon: FreeIcon, color: '#E8A838', href: undefined },
  { key: 'data', icon: DataIcon2, color: '#14A3A8', href: undefined },
] as const

const FAQ_KEYS = ['cost', 'whoCanApply', 'needCql', 'data', 'portability', 'standards', 'support'] as const

function SectionTitle({ title, subtitle, id }: { title: string; subtitle?: string; id?: string }) {
  return (
    <Box id={id} sx={{ textAlign: 'center', mb: 5, scrollMarginTop: 80 }}>
      <Typography variant="h4" component="h2" sx={{ fontWeight: 700, mb: 1 }}>{title}</Typography>
      {subtitle && <Typography variant="body1" color="text.secondary" sx={{ maxWidth: 720, mx: 'auto' }}>{subtitle}</Typography>}
    </Box>
  )
}

function Screenshot({ src, alt }: { src: string; alt: string }) {
  return (
    <Box
      component="img"
      src={src}
      alt={alt}
      loading="lazy"
      sx={{
        width: '100%',
        display: 'block',
        borderRadius: 2,
        border: '1px solid',
        borderColor: 'divider',
        boxShadow: (theme) => `0 12px 32px ${alpha(theme.palette.common.black, 0.12)}`,
        bgcolor: 'background.paper',
      }}
    />
  )
}

export default function HomePage() {
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation('common')
  const navigate = useNavigate()
  const scrollTo = (id: string) => document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })

  const navItems: Array<{ key: string; onClick: () => void }> = [
    { key: 'features', onClick: () => scrollTo('capabilities') },
    { key: 'howItWorks', onClick: () => scrollTo('how-it-works') },
    { key: 'docs', onClick: () => navigate('/docs') },
    { key: 'learn', onClick: () => navigate('/learn') },
    { key: 'templates', onClick: () => navigate('/templates') },
    { key: 'faq', onClick: () => scrollTo('faq') },
  ]

  return (
    <>
      <Helmet>
        <title>{t('home.seo.title')}</title>
        <meta name="description" content={t('home.seo.description')} />
        <meta name="robots" content="index, follow" />
        <link rel="canonical" href="https://twcql.com/" />
        <meta property="og:title" content={t('home.seo.title')} />
        <meta property="og:description" content={t('home.seo.description')} />
        <meta property="og:url" content="https://twcql.com/" />
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
        {/* Top bar */}
        <Box
          component="header"
          sx={(theme) => ({
            position: 'sticky', top: 0, zIndex: 10,
            background: heroGradient(theme),
            px: { xs: 2, md: 4 }, py: 1.25,
            display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2,
          })}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <MedicalIcon sx={{ color: 'common.white', fontSize: 24 }} />
            <Typography variant="subtitle1" sx={{ fontWeight: 700, color: 'common.white' }}>{tc('app.title')}</Typography>
          </Box>
          <Box component="nav" aria-label={t('home.nav.ariaLabel')} sx={{ display: 'flex', alignItems: 'center', gap: 0.5, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
            {navItems.map((item) => (
              <Button
                key={item.key}
                onClick={item.onClick}
                size="small"
                sx={{ color: 'common.white', textTransform: 'none', display: { xs: item.key === 'templates' ? 'none' : 'inline-flex', md: 'inline-flex' } }}
              >
                {t(`home.nav.${item.key}`)}
              </Button>
            ))}
            <Button
              size="small"
              variant="outlined"
              startIcon={<LoginIcon />}
              onClick={() => navigate('/login')}
              sx={(theme) => ({ color: 'common.white', borderColor: alpha(theme.palette.common.white, 0.6), textTransform: 'none', ml: 0.5, '&:hover': { borderColor: 'common.white', bgcolor: alpha(theme.palette.common.white, 0.08) } })}
            >
              {t('home.nav.login')}
            </Button>
            <LanguageMenu />
          </Box>
        </Box>

        {/* Hero */}
        <Box component="section" sx={(theme) => ({ background: heroGradient(theme), color: 'common.white', pt: { xs: 7, md: 10 }, pb: { xs: 7, md: 10 } })}>
          <Container maxWidth="lg">
            <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, alignItems: 'center', gap: { xs: 5, md: 6 } }}>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="overline" sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.8), letterSpacing: 2 })}>
                  {t('home.hero.eyebrow')}
                </Typography>
                <Typography variant="h3" component="h1" sx={{ fontWeight: 800, lineHeight: 1.2, mt: 1, mb: 2, fontSize: { xs: '2rem', md: '2.75rem' } }}>
                  {t('home.hero.title')}
                </Typography>
                <Typography variant="h6" component="p" sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.9), fontWeight: 400, mb: 3 })}>
                  {t('home.hero.subtitle')}
                </Typography>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mb: 2 }}>
                  <Button
                    variant="contained"
                    size="large"
                    onClick={() => navigate('/apply')}
                    sx={(theme) => ({ bgcolor: 'common.white', color: '#0D7377', fontWeight: 700, textTransform: 'none', '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.9) } })}
                  >
                    {t('home.hero.primaryCta')}
                  </Button>
                  <Button
                    variant="outlined"
                    size="large"
                    onClick={() => scrollTo('how-it-works')}
                    sx={(theme) => ({ color: 'common.white', borderColor: alpha(theme.palette.common.white, 0.6), textTransform: 'none', '&:hover': { borderColor: 'common.white', bgcolor: alpha(theme.palette.common.white, 0.08) } })}
                  >
                    {t('home.hero.secondaryCta')}
                  </Button>
                  <Button
                    size="large"
                    startIcon={<LearnIcon />}
                    onClick={() => navigate('/learn')}
                    sx={{ color: 'common.white', textTransform: 'none' }}
                  >
                    {t('home.hero.tertiaryCta')}
                  </Button>
                </Stack>
                <Typography variant="body2" sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.75) })}>
                  {t('home.hero.note')}
                </Typography>
              </Box>
              <Box sx={{ flex: 1.1, minWidth: 0, width: '100%' }}>
                <Screenshot src="/screenshots/hero-builder.jpg" alt={t('home.hero.imageAlt')} />
              </Box>
            </Box>
          </Container>
        </Box>

        {/* Audiences */}
        <Box component="section" sx={{ py: { xs: 7, md: 9 } }}>
          <Container maxWidth="lg">
            <SectionTitle title={t('home.audiences.title')} subtitle={t('home.audiences.subtitle')} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 3 }}>
              {AUDIENCES.map(({ key, icon: Icon, color }) => (
                <Paper key={key} variant="outlined" sx={{ p: 3, borderRadius: 3, height: '100%' }}>
                  <Box sx={{ width: 44, height: 44, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: alpha(color, 0.12), mb: 2 }}>
                    <Icon sx={{ color }} />
                  </Box>
                  <Typography variant="h6" sx={{ fontWeight: 700, mb: 1 }}>{t(`home.audiences.${key}.title`)}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{t(`home.audiences.${key}.description`)}</Typography>
                  <Stack component="ul" spacing={0.5} sx={{ pl: 2.5, m: 0 }}>
                    {([1, 2, 3] as const).map((n) => (
                      <Typography key={n} component="li" variant="body2">{t(`home.audiences.${key}.point${n}`)}</Typography>
                    ))}
                  </Stack>
                </Paper>
              ))}
            </Box>
          </Container>
        </Box>

        {/* How it works */}
        <Box component="section" sx={{ py: { xs: 7, md: 9 }, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) }}>
          <Container maxWidth="lg">
            <SectionTitle id="how-it-works" title={t('home.howItWorks.title')} subtitle={t('home.howItWorks.subtitle')} />
            <Stack spacing={{ xs: 6, md: 8 }}>
              {STEPS.map(({ key, icon: Icon, image, color }, index) => (
                <Box
                  key={key}
                  sx={{ display: 'flex', flexDirection: { xs: 'column', md: index % 2 === 0 ? 'row' : 'row-reverse' }, alignItems: 'center', gap: { xs: 3, md: 6 } }}
                >
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1.5 }}>
                      <Box sx={{ width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: color, color: 'common.white', fontWeight: 700 }}>
                        {index + 1}
                      </Box>
                      <Icon sx={{ color }} />
                      <Typography variant="h5" component="h3" sx={{ fontWeight: 700 }}>{t(`home.howItWorks.${key}.title`)}</Typography>
                    </Stack>
                    <Typography variant="body1" color="text.secondary" sx={{ mb: 1.5 }}>{t(`home.howItWorks.${key}.description`)}</Typography>
                    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                      {([1, 2, 3] as const).map((n) => (
                        <Chip key={n} size="small" variant="outlined" label={t(`home.howItWorks.${key}.tag${n}`)} />
                      ))}
                    </Stack>
                  </Box>
                  <Box sx={{ flex: 1.2, minWidth: 0, width: '100%' }}>
                    <Screenshot src={image} alt={t(`home.howItWorks.${key}.imageAlt`)} />
                  </Box>
                </Box>
              ))}
            </Stack>
          </Container>
        </Box>

        {/* Capabilities */}
        <Box component="section" sx={{ py: { xs: 7, md: 9 } }}>
          <Container maxWidth="lg">
            <SectionTitle id="capabilities" title={t('home.capabilities.title')} subtitle={t('home.capabilities.subtitle')} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', md: 'repeat(3, 1fr)' }, gap: 3 }}>
              {CAPABILITY_GROUPS.map(({ key, icon: Icon, color, items }) => (
                <Paper key={key} variant="outlined" sx={{ p: 3, borderRadius: 3 }}>
                  <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 2 }}>
                    <Box sx={{ width: 40, height: 40, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: alpha(color, 0.12) }}>
                      <Icon sx={{ color }} />
                    </Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{t(`home.capabilities.${key}.title`)}</Typography>
                  </Stack>
                  <Stack spacing={1.25}>
                    {items.map((item) => (
                      <Box key={item}>
                        {/* body2, not subtitle2: the theme uppercases subtitle2 and would mangle "eCQM" / "MADiE" */}
                        <Typography variant="body2" sx={{ fontWeight: 700 }}>{t(`home.capabilities.${key}.${item}.title`)}</Typography>
                        <Typography variant="body2" color="text.secondary">{t(`home.capabilities.${key}.${item}.description`)}</Typography>
                      </Box>
                    ))}
                  </Stack>
                </Paper>
              ))}
            </Box>
          </Container>
        </Box>

        {/* Demo gallery */}
        <Box component="section" sx={{ py: { xs: 7, md: 9 }, bgcolor: (theme) => alpha(theme.palette.primary.main, 0.04) }}>
          <Container maxWidth="lg">
            <SectionTitle id="demo" title={t('home.demo.title')} subtitle={t('home.demo.subtitle')} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 4 }}>
              {DEMO_SHOTS.map(({ key, image }) => (
                <Box key={key}>
                  <Screenshot src={image} alt={t(`home.demo.${key}.imageAlt`)} />
                  <Typography variant="subtitle1" sx={{ fontWeight: 600, mt: 1.5 }}>{t(`home.demo.${key}.title`)}</Typography>
                  <Typography variant="body2" color="text.secondary">{t(`home.demo.${key}.description`)}</Typography>
                </Box>
              ))}
            </Box>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'center', mt: 5 }}>
              <Button variant="outlined" onClick={() => navigate('/templates')} sx={{ textTransform: 'none' }}>{t('home.demo.templatesCta')}</Button>
              <Button variant="outlined" onClick={() => navigate('/docs')} sx={{ textTransform: 'none' }}>{t('home.demo.docsCta')}</Button>
            </Stack>
          </Container>
        </Box>

        {/* Standards + CQL / TWCORE explainer */}
        <Box component="section" sx={{ pt: { xs: 7, md: 9 } }}>
          <Container maxWidth="lg">
            <SectionTitle title={t('home.standards.title')} subtitle={t('home.standards.subtitle')} />
            <Stack direction="row" spacing={1} sx={{ justifyContent: 'center', flexWrap: 'wrap', gap: 1 }}>
              {STANDARDS.map((s) => <Chip key={s} label={s} color="primary" variant="outlined" />)}
            </Stack>
          </Container>
        </Box>
        <CqlShowcase />

        {/* Trust + FAQ */}
        <Box component="section" sx={{ py: { xs: 7, md: 9 } }}>
          <Container maxWidth="lg">
            <SectionTitle id="trust" title={t('home.trust.title')} subtitle={t('home.trust.subtitle')} />
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' }, gap: 3 }}>
              {TRUST.map(({ key, icon: Icon, color, href }) => (
                <Paper key={key} variant="outlined" sx={{ p: 3, borderRadius: 3 }} data-testid={`trust-${key}`}>
                  <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', mb: 1.5 }}>
                    <Box sx={{ width: 40, height: 40, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: alpha(color, 0.12) }}>
                      <Icon sx={{ color }} />
                    </Box>
                    <Typography variant="h6" sx={{ fontWeight: 700 }}>{t(`home.trust.${key}.title`)}</Typography>
                  </Stack>
                  <Typography variant="body2" color="text.secondary">{t(`home.trust.${key}.description`)}</Typography>
                  {href && (
                    <Link href={href} target="_blank" rel="noopener noreferrer" variant="body2" sx={{ display: 'inline-block', mt: 1.5, fontWeight: 600 }}>
                      {t(`home.trust.${key}.link`)}
                    </Link>
                  )}
                </Paper>
              ))}
            </Box>

            <Box sx={{ mt: { xs: 7, md: 9 } }}>
              <SectionTitle id="faq" title={t('home.faq.title')} subtitle={t('home.faq.subtitle')} />
              <Box sx={{ maxWidth: 880, mx: 'auto' }}>
                {FAQ_KEYS.map((key) => (
                  <Accordion key={key} disableGutters elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: '12px !important', mb: 1.5, '&:before': { display: 'none' } }}>
                    <AccordionSummary expandIcon={<ExpandMoreIcon />} aria-controls={`faq-${key}-content`} id={`faq-${key}-header`}>
                      <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{t(`home.faq.${key}.q`)}</Typography>
                    </AccordionSummary>
                    <AccordionDetails sx={{ pt: 0 }}>
                      <Typography variant="body2" color="text.secondary">{t(`home.faq.${key}.a`)}</Typography>
                    </AccordionDetails>
                  </Accordion>
                ))}
              </Box>
            </Box>
          </Container>
        </Box>

        {/* Closing CTA */}
        <Box component="section" sx={(theme) => ({ background: heroGradient(theme), color: 'common.white', py: { xs: 7, md: 8 } })}>
          <Container maxWidth="md" sx={{ textAlign: 'center' }}>
            <Typography variant="h4" component="h2" sx={{ fontWeight: 700, mb: 1.5 }}>{t('home.cta.title')}</Typography>
            <Typography variant="body1" sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.85), mb: 3 })}>{t('home.cta.description')}</Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'center' }}>
              <Button variant="contained" size="large" onClick={() => navigate('/apply')} sx={(theme) => ({ bgcolor: 'common.white', color: '#0D7377', fontWeight: 700, textTransform: 'none', '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.9) } })}>
                {t('home.cta.apply')}
              </Button>
              <Button variant="outlined" size="large" onClick={() => navigate('/login')} sx={(theme) => ({ color: 'common.white', borderColor: alpha(theme.palette.common.white, 0.6), textTransform: 'none' })}>
                {t('home.cta.login')}
              </Button>
            </Stack>
          </Container>
        </Box>
        <PublicFooter />
      </Box>
    </>
  )
}
