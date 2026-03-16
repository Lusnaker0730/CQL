import { Box, Typography, Button, Container, IconButton, Menu, MenuItem } from '@mui/material'
import {
  LocalHospital as MedicalIcon,
  Translate as TranslateIcon,
  School as LearnIcon,
} from '@mui/icons-material'
import { useNavigate } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import LoginForm from '../components/landing/LoginForm'
import FeatureCards from '../components/landing/FeatureCards'
import CqlShowcase from '../components/landing/CqlShowcase'

export default function LandingPage() {
  const { t, i18n } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const navigate = useNavigate()
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null)

  const handleLanguageChange = (lang: string) => {
    i18n.changeLanguage(lang)
    setLangAnchor(null)
  }

  return (
    <>
    <Helmet>
      <title>{`${tc('app.title')} — Clinical Quality Language`}</title>
      <meta name="description" content={`${tc('app.title')} — ${t('seoDescription')}`} />
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
          <MedicalIcon sx={{ color: 'white', fontSize: 24 }} />
          <Typography variant="subtitle1" fontWeight={700} sx={{ color: 'white' }}>
            {tc('app.title')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
          <Button
            startIcon={<LearnIcon />}
            onClick={() => navigate('/learn')}
            sx={{ color: 'rgba(255,255,255,0.9)', textTransform: 'none', fontWeight: 600 }}
          >
            {t('hero.tryDemo')}
          </Button>
          <IconButton onClick={(e) => setLangAnchor(e.currentTarget)} sx={{ color: 'rgba(255,255,255,0.8)' }}>
            <TranslateIcon fontSize="small" />
          </IconButton>
          <Menu
            anchorEl={langAnchor}
            open={Boolean(langAnchor)}
            onClose={() => setLangAnchor(null)}
          >
            <MenuItem onClick={() => handleLanguageChange('en')} selected={i18n.language === 'en'}>
              {tc('language.english')}
            </MenuItem>
            <MenuItem onClick={() => handleLanguageChange('zh-TW')} selected={i18n.language === 'zh-TW'}>
              {tc('language.traditionalChinese')}
            </MenuItem>
          </Menu>
        </Box>
      </Box>

      {/* Hero Section */}
      <Box
        sx={{
          background: 'linear-gradient(135deg, #0D7377 0%, #1B3A5C 50%, #0F2440 100%)',
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
            background: 'radial-gradient(circle, rgba(20,163,168,0.15) 0%, transparent 60%)',
            pointerEvents: 'none',
          },
        }}
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
                fontWeight={800}
                sx={{
                  color: 'white',
                  mb: 2,
                  lineHeight: 1.3,
                  fontSize: { xs: '1.8rem', sm: '2.2rem', md: '2.6rem' },
                }}
              >
                {tc('app.title')}
              </Typography>
              <Typography
                variant="h6"
                sx={{
                  color: 'rgba(255,255,255,0.85)',
                  mb: 2,
                  fontWeight: 400,
                  lineHeight: 1.6,
                  fontSize: { xs: '1rem', md: '1.15rem' },
                }}
              >
                {t('hero.tagline')}
              </Typography>
              <Typography
                variant="body1"
                sx={{
                  color: 'rgba(255,255,255,0.65)',
                  mb: 4,
                  lineHeight: 1.8,
                  maxWidth: 520,
                }}
              >
                {t('hero.description')}
              </Typography>
              <Box sx={{ display: 'flex', gap: 2 }}>
                <Button
                  variant="contained"
                  size="large"
                  startIcon={<LearnIcon />}
                  onClick={() => navigate('/learn')}
                  sx={{
                    bgcolor: 'white',
                    color: '#0D7377',
                    fontWeight: 700,
                    px: 3,
                    py: 1.2,
                    borderRadius: 2,
                    textTransform: 'none',
                    '&:hover': { bgcolor: 'rgba(255,255,255,0.9)' },
                  }}
                >
                  {t('hero.learnMore')}
                </Button>
              </Box>
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

      {/* Footer */}
      <Box
        sx={{
          py: 3,
          px: 3,
          bgcolor: '#0F2440',
          textAlign: 'center',
        }}
      >
        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, mb: 1.5 }}>
          {[
            { label: 'CQL Specification', href: 'https://cql.hl7.org/' },
            { label: 'HL7 FHIR', href: 'https://www.hl7.org/fhir/' },
            { label: 'TWCORE IG', href: 'https://twcore.mohw.gov.tw/ig/twcore/' },
            { label: 'CDS Hooks', href: 'https://cds-hooks.org/' },
          ].map((link) => (
            <Typography
              key={link.label}
              component="a"
              href={link.href}
              target="_blank"
              rel="noopener noreferrer"
              variant="body2"
              sx={{
                color: 'rgba(255,255,255,0.6)',
                textDecoration: 'none',
                '&:hover': { color: '#14A3A8' },
              }}
            >
              {link.label}
            </Typography>
          ))}
        </Box>
        <Typography variant="caption" sx={{ color: 'rgba(255,255,255,0.35)' }}>
          {tc('app.title')} — Clinical Quality Language
        </Typography>
      </Box>
    </Box>
    </>
  )
}
