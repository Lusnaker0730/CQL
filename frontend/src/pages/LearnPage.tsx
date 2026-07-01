import { useState, useEffect, useCallback } from 'react'
import {
  Box,
  Container,
  Tabs,
  Tab,
  Button,
  Typography,
  IconButton,
  Menu,
  MenuItem,
  useMediaQuery,
  useTheme,
} from '@mui/material'
import { alpha } from '@mui/material/styles'
import {
  LocalHospital as MedicalIcon,
  ArrowBack as BackIcon,
  Translate as TranslateIcon,
  MenuBook as IntroIcon,
  Hub as ConceptIcon,
  Public as TwcoreIcon,
  PlayCircle as ExampleIcon,
  RocketLaunch as StartIcon,
  Code as PlaygroundIcon,
  Quiz as QuizIcon,
  School as AdvancedIcon,
  BugReport as TroubleshootingIcon,
  ListAlt as CheatSheetIcon,
  DataObject as DataObjectIcon,
  AccountTree as AccountTreeIcon,
  Assessment as AssessmentIcon,
} from '@mui/icons-material'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { Helmet } from 'react-helmet-async'
import { useTranslation } from 'react-i18next'
import CqlIntroduction from '../components/learn/CqlIntroduction'
import ConceptGuide from '../components/learn/ConceptGuide'
import TwcoreGuide from '../components/learn/TwcoreGuide'
import InteractiveExamples from '../components/learn/InteractiveExamples'
import QuickStartGuide from '../components/learn/QuickStartGuide'
import AdvancedTopics from '../components/learn/AdvancedTopics'
import TroubleshootingGuide from '../components/learn/TroubleshootingGuide'
import CqlCheatSheet from '../components/learn/CqlCheatSheet'
import CqlPlayground from '../components/learn/CqlPlayground'
import CqlQuiz from '../components/learn/CqlQuiz'
import LanguageReference from '../components/learn/LanguageReference'
import FhirPathElmGuide from '../components/learn/FhirPathElmGuide'
import EcqmTutorial from '../components/learn/EcqmTutorial'

const TAB_KEYS = [
  'introduction', 'concepts', 'twcore', 'examples', 'quickStart',
  'advanced', 'troubleshooting', 'cheatSheet',
  'languageRef', 'fhirpathElm', 'ecqmTutorial',
  'playground', 'quiz',
] as const
const TAB_ICONS = [
  IntroIcon, ConceptIcon, TwcoreIcon, ExampleIcon, StartIcon,
  AdvancedIcon, TroubleshootingIcon, CheatSheetIcon,
  DataObjectIcon, AccountTreeIcon, AssessmentIcon,
  PlaygroundIcon, QuizIcon,
]

export default function LearnPage() {
  const { t, i18n } = useTranslation('landing')
  const { t: tc } = useTranslation()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const theme = useTheme()
  const isMobile = useMediaQuery(theme.breakpoints.down('md'))
  const [langAnchor, setLangAnchor] = useState<null | HTMLElement>(null)

  const tabParam = searchParams.get('tab')
  const tabIndex = TAB_KEYS.indexOf(tabParam as typeof TAB_KEYS[number])
  const currentTab = tabIndex >= 0 ? tabIndex : 0

  const handleTabChange = useCallback((_: React.SyntheticEvent, newValue: number) => {
    setSearchParams({ tab: TAB_KEYS[newValue] })
  }, [setSearchParams])

  // Track learning progress in localStorage
  useEffect(() => {
    const key = `cql-learn-visited-${TAB_KEYS[currentTab]}`
    localStorage.setItem(key, 'true')
  }, [currentTab])

  const currentTabKey = TAB_KEYS[currentTab]
  const seoDescription = t(`learn.seo.${currentTabKey}`, t('learn.seo.defaultDescription'))
  const canonicalUrl = currentTab === 0
    ? 'https://twcql.com/learn'
    : `https://twcql.com/learn?tab=${currentTabKey}`

  return (
    <>
      <Helmet>
        <title>{`${t(`learn.nav.${currentTabKey}`)} — ${tc('app.title')}`}</title>
        <meta name="description" content={seoDescription} />
        <link rel="canonical" href={canonicalUrl} />
        <meta property="og:title" content={`${t(`learn.nav.${currentTabKey}`)} — ${tc('app.title')}`} />
        <meta property="og:description" content={seoDescription} />
        <meta property="og:url" content={canonicalUrl} />
        <meta property="og:type" content="article" />
      </Helmet>
      <Box sx={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', bgcolor: 'background.default' }}>
        {/* Header */}
        <Box
          sx={{
            background: `linear-gradient(135deg, ${theme.palette.primary.main} 0%, ${theme.palette.secondary.main} 100%)`,
            px: 3,
            py: 1.5,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
          }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button
              startIcon={<BackIcon />}
              onClick={() => navigate('/login')}
              sx={{ color: alpha(theme.palette.common.white, 0.9), textTransform: 'none', mr: 2 }}
            >
              {t('learn.backToHome')}
            </Button>
            <MedicalIcon sx={{ color: 'common.white', fontSize: 22 }} />
            <Typography
              variant="subtitle1"
              sx={{
                fontWeight: 700,
                color: 'common.white'
              }}>
              {tc('app.title')}
            </Typography>
          </Box>
          <Box>
            <IconButton onClick={(e) => setLangAnchor(e.currentTarget)} sx={{ color: alpha(theme.palette.common.white, 0.8) }}>
              <TranslateIcon fontSize="small" />
            </IconButton>
            <Menu
              anchorEl={langAnchor}
              open={Boolean(langAnchor)}
              onClose={() => setLangAnchor(null)}
            >
              <MenuItem onClick={() => { i18n.changeLanguage('en'); setLangAnchor(null) }} selected={i18n.language === 'en'}>
                {tc('language.english')}
              </MenuItem>
              <MenuItem onClick={() => { i18n.changeLanguage('zh-TW'); setLangAnchor(null) }} selected={i18n.language === 'zh-TW'}>
                {tc('language.traditionalChinese')}
              </MenuItem>
            </Menu>
          </Box>
        </Box>

        {/* Translation attribution — content adapted from HL7 CQL official site */}
        <Box sx={(t) => ({ bgcolor: alpha(t.palette.info.main, 0.06), borderBottom: 1, borderColor: 'divider' })}>
          <Container maxWidth="lg" sx={{ py: 0.75 }}>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('hero.tutorialAttribution')}{' '}
              <a
                href="https://cql.hl7.org/"
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: 'inherit', textDecoration: 'underline' }}
              >
                cql.hl7.org
              </a>
            </Typography>
          </Container>
        </Box>

        {/* Tabs */}
        <Box sx={{ borderBottom: 1, borderColor: 'divider', bgcolor: 'background.paper' }}>
          <Container maxWidth="lg">
            <Tabs
              value={currentTab}
              onChange={handleTabChange}
              variant={isMobile ? 'scrollable' : 'scrollable'}
              scrollButtons="auto"
              sx={{
                '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, minHeight: 52 },
              }}
            >
              {TAB_KEYS.map((key, i) => {
                const Icon = TAB_ICONS[i]
                return (
                  <Tab
                    key={key}
                    icon={<Icon sx={{ fontSize: 20 }} />}
                    iconPosition="start"
                    label={t(`learn.nav.${key}`)}
                  />
                )
              })}
            </Tabs>
          </Container>
        </Box>

        {/* Content */}
        <Box sx={{ flex: 1, py: 4 }}>
          <Container maxWidth="lg">
            {currentTab === 0 && <CqlIntroduction />}
            {currentTab === 1 && <ConceptGuide />}
            {currentTab === 2 && <TwcoreGuide />}
            {currentTab === 3 && <InteractiveExamples />}
            {currentTab === 4 && <QuickStartGuide />}
            {currentTab === 5 && <AdvancedTopics />}
            {currentTab === 6 && <TroubleshootingGuide />}
            {currentTab === 7 && <CqlCheatSheet />}
            {currentTab === 8 && <LanguageReference />}
            {currentTab === 9 && <FhirPathElmGuide />}
            {currentTab === 10 && <EcqmTutorial />}
            {currentTab === 11 && <CqlPlayground />}
            {currentTab === 12 && <CqlQuiz />}
          </Container>
        </Box>

        {/* Footer */}
        <Box sx={{ py: 2, px: 3, borderTop: 1, borderColor: 'divider', textAlign: 'center' }}>
          <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3 }}>
            {[
              { label: 'CQL Specification', href: 'https://cql.hl7.org/' },
              { label: 'HL7 FHIR', href: 'https://www.hl7.org/fhir/' },
              { label: 'TWCORE IG', href: 'https://twcore.mohw.gov.tw/ig/twcore/' },
            ].map((link) => (
              <Typography
                key={link.label}
                component="a"
                href={link.href}
                target="_blank"
                rel="noopener noreferrer"
                variant="caption"
                sx={{ color: 'text.secondary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
              >
                {link.label}
              </Typography>
            ))}
          </Box>
        </Box>
      </Box>
    </>
  );
}
