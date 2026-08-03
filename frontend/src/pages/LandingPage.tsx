import { Box, Typography, Button, Container } from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  LocalHospital as MedicalIcon,
  School as LearnIcon,
} from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { useTranslation } from 'react-i18next'
import LanguageMenu from '../components/common/LanguageMenu'
import PublicFooter from '../components/common/PublicFooter'
import LoginForm from '../components/landing/LoginForm'
import FeatureCards from '../components/landing/FeatureCards'
import CqlShowcase from '../components/landing/CqlShowcase'

export default function LandingPage() {
  const { t } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const navigate = useNavigate()

  return (
    <>
      <Helmet>
        <title>{`${tc('app.title')} — Clinical Quality Language`}</title>
        <meta name="description" content={`${tc('app.title')} — ${t('seoDescription')}`} />
        {/* Login/landing page is an auth gateway, not a content page — tell crawlers
            to skip it. Indexing auth pages just dilutes search signal for the real
            content pages (/ and /learn) and creates "Discovered – not indexed"
            backlog in Search Console. Keep the canonical tag so if the page IS
            found via a link, the canonical is still self-consistent. */}
        <meta name="robots" content="noindex, follow" />
        <link rel="canonical" href="https://twcql.com/login" />
        <meta property="og:title" content={`${tc('app.title')} — Clinical Quality Language`} />
        <meta property="og:url" content="https://twcql.com/login" />
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column' }}>
        {/* Top bar */}
        <Box
          sx={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            zIndex: 10,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            px: 3,
            py: 1.5,
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <MedicalIcon sx={{ color: 'common.white', fontSize: 24 }} />
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 700,
                color: 'common.white'
              }}>
              {tc('app.title')}
            </Typography>
          </Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button
              startIcon={<LearnIcon />}
              onClick={() => navigate('/learn')}
              sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.9), textTransform: 'none', fontWeight: 600 })}
            >
              {t('hero.tryDemo')}
            </Button>
            <Button
              onClick={() => navigate('/templates')}
              sx={(theme) => ({ color: alpha(theme.palette.common.white, 0.9), textTransform: 'none', fontWeight: 600 })}
            >
              {t('catalog.pageTitle')}
            </Button>
            <LanguageMenu />
          </Box>
        </Box>

        {/* Hero Section */}
        <Box
          sx={(theme) => ({
            background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 50%, ${theme.palette.secondary.dark} 100%)`,
            pt: { xs: 10, md: 8 },
            pb: { xs: 6, md: 8 },
            px: 3,
            position: 'relative',
            overflow: 'hidden',
            '&::before': {
              content: '""',
              position: 'absolute',
              top: '-50%',
              right: '-20%',
              width: '60%',
              height: '200%',
              background: `radial-gradient(circle, ${alpha(theme.palette.primary.light, 0.15)} 0%, transparent 60%)`,
              pointerEvents: 'none',
            },
          })}
        >
          <Container maxWidth="lg">
            <Box
              sx={{
                display: 'flex',
                flexDirection: { xs: 'column', md: 'row' },
                alignItems: { xs: 'center', md: 'center' },
                gap: { xs: 5, md: 6 },
                minHeight: { md: 480 },
              }}
            >
              {/* Left: Introduction */}
              <Box sx={{ flex: 1, maxWidth: { md: '55%' } }}>
                <Typography
                  variant="h3"
                  sx={{
                    fontWeight: 800,
                    color: 'common.white',
                    mb: 2,
                    lineHeight: 1.3,
                    fontSize: { xs: '1.8rem', sm: '2.2rem', md: '2.6rem' }
                  }}>
                  {tc('app.title')}
                </Typography>
                <Typography
                  variant="h6"
                  sx={(theme) => ({
                    color: alpha(theme.palette.common.white, 0.85),
                    mb: 2,
                    fontWeight: 400,
                    lineHeight: 1.6,
                    fontSize: { xs: '1rem', md: '1.15rem' },
                  })}
                >
                  {t('hero.tagline')}
                </Typography>
                <Typography
                  variant="body1"
                  sx={(theme) => ({
                    color: alpha(theme.palette.common.white, 0.65),
                    mb: 4,
                    lineHeight: 1.8,
                    maxWidth: 520,
                  })}
                >
                  {t('hero.description')}
                </Typography>
                <Box sx={{ display: 'flex', gap: 2 }}>
                  <Button
                    variant="contained"
                    size="large"
                    startIcon={<LearnIcon />}
                    onClick={() => navigate('/learn')}
                    sx={(theme) => ({
                      bgcolor: 'common.white',
                      color: theme.palette.primary.main,
                      fontWeight: 700,
                      px: 3,
                      py: 1.2,
                      borderRadius: 2,
                      textTransform: 'none',
                      '&:hover': { bgcolor: alpha(theme.palette.common.white, 0.9) },
                    })}
                  >
                    {t('hero.learnMore')}
                  </Button>
                  <Button
                    variant="outlined"
                    size="large"
                    onClick={() => navigate('/apply')}
                    sx={(theme) => ({
                      color: 'common.white',
                      borderColor: alpha(theme.palette.common.white, 0.6),
                      fontWeight: 700,
                      px: 3,
                      py: 1.2,
                      borderRadius: 2,
                      textTransform: 'none',
                      '&:hover': { borderColor: 'common.white', bgcolor: alpha(theme.palette.common.white, 0.08) },
                    })}
                  >
                    {t('hero.applyCta')}
                  </Button>
                </Box>
                <Typography
                  variant="caption"
                  sx={(theme) => ({
                    display: 'block',
                    mt: 1.5,
                    color: alpha(theme.palette.common.white, 0.6),
                    fontStyle: 'italic',
                  })}
                >
                  {t('hero.tutorialAttribution')}
                </Typography>
              </Box>

              {/* Right: Login Form */}
              <Box sx={{ flexShrink: 0 }}>
                <LoginForm />
              </Box>
            </Box>
          </Container>
        </Box>

        {/* Feature Cards */}
        <Box sx={{ bgcolor: 'background.default' }}>
          <Container maxWidth="lg">
            <FeatureCards />
          </Container>
        </Box>

        {/* CQL Showcase */}
        <CqlShowcase />

        <PublicFooter />
      </Box>
    </>
  );
}
